/*****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 ****************************************************************/
/*
 * Copyright 2010 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package msi.gama.metamodel.topology.experimental;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractQueue;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A hash table supporting full concurrency of retrievals, adjustable expected concurrency for updates, and a maximum
 * capacity to bound the map by. This implementation differs from {@link ConcurrentHashMap} in that it maintains a page
 * replacement algorithm that is used to evict an entry when the map has exceeded its capacity. Unlike the
 * <tt>Java Collections Framework</tt>, this map does not have a publicly visible constructor and instances are created
 * through a {@link Builder}.
 * <p>
 * An entry is evicted from the map when the <tt>weighted capacity</tt> exceeds its <tt>maximum weighted capacity</tt>
 * threshold. A {@link Weigher} instance determines how many units of capacity that a value consumes. The default
 * weigher assigns each value a weight of <tt>1</tt> to bound the map by the total number of key-value pairs. A map that
 * holds collections may choose to weigh values by the number of elements in the collection and bound the map by the
 * total number of elements that it contains. A change to a value that modifies its weight requires that an update
 * operation is performed on the map.
 * <p>
 * An {@link EvictionListener} may be supplied for notification when an entry is evicted from the map. This listener is
 * invoked on a caller's thread and will not block other threads from operating on the map. An implementation should be
 * aware that the caller's thread will not expect long execution times or failures as a side effect of the listener
 * being notified. Execution safety and a fast turn around time can be achieved by performing the operation
 * asynchronously, such as by submitting a task to an {@link java.util.concurrent.ExecutorService}.
 * <p>
 * The <tt>concurrency level</tt> determines the number of threads that can concurrently modify the table. Using a
 * significantly higher or lower value than needed can waste space or lead to thread contention, but an estimate within
 * an order of magnitude of the ideal value does not usually have a noticeable impact. Because placement in hash tables
 * is essentially random, the actual concurrency will vary.
 * <p>
 * This class and its views and iterators implement all of the <em>optional</em> methods of the {@link Map} and
 * {@link Iterator} interfaces.
 * <p>
 * Like {@link java.util.Hashtable} but unlike {@link HashMap}, this class does <em>not</em> allow <tt>null</tt> to be
 * used as a key or value. Unlike {@link java.util.LinkedHashMap}, this class does <em>not</em> provide predictable
 * iteration order. A snapshot of the keys and entries may be obtained in ascending and descending order of retention.
 * 
 * @param <K>
 *            the type of keys maintained by this map
 * @param <V>
 *            the type of mapped values
 */
// based on http://concurrentlinkedhashmap.googlecode.com/svn/trunk r754
public class ConcurrentLinkedHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, Serializable {

	public static interface EvictionListener<K, V> {

		/**
		 * A call-back notification that the entry was evicted.
		 * 
		 * @param key
		 *            the entry's key
		 * @param value
		 *            the entry's value
		 */
		void onEviction(K key, V value);
	}

	public static interface Weigher<V> {

		/**
		 * Measures an object's weight to determine how many units of capacity that the value consumes. A value must
		 * consume a minimum of one unit.
		 * 
		 * @param value
		 *            the object to weigh
		 * @return the object's weight
		 */
		int weightOf(V value);
	}

	/*
	 * This class performs a best-effort bounding of a ConcurrentHashMap using a page-replacement algorithm to determine
	 * which entries to evict when the capacity is exceeded. The page replacement algorithm's data structures are kept
	 * eventually consistent with the map. An update to the map and recording of reads may not be immediately reflected
	 * on the algorithm's data structures. These structures are guarded by a lock and operations are applied in batches
	 * to avoid lock contention. The penalty of applying the batches is spread across threads so that the amortized cost
	 * is slightly higher than performing just the ConcurrentHashMap operation. A memento of the reads and writes that
	 * were performed on the map are recorded in a buffer. These buffers are drained at the first opportunity after a
	 * write or when a buffer exceeds a threshold size. A mostly strict ordering is achieved by observing that each
	 * buffer is in a weakly sorted order relative to the last drain. This allows the buffers to be merged in O(n) time
	 * so that the operations are run in the expected order. Due to a lack of a strict ordering guarantee, a task can be
	 * executed out-of-order, such as a removal followed by its addition. The state of the entry is encoded within the
	 * value's weight. Alive: The entry is in both the hash-table and the page replacement policy. This is represented
	 * by a positive weight. Retired: The entry is not in the hash-table and is pending removal from the page
	 * replacement policy. This is represented by a negative weight. Dead: The entry is not in the hash-table and is not
	 * in the page replacement policy. This is represented by a weight of zero. The Least Recently Used page replacement
	 * algorithm was chosen due to its simplicity, high hit rate, and ability to be implemented with O(1) time
	 * complexity.
	 */

	/** The maximum weighted capacity of the map. */
	static final int MAXIMUM_CAPACITY = 1 << 30;

	/** The maximum weight of a value. */
	static final int MAXIMUM_WEIGHT = 1 << 29;

	/** The maximum number of pending operations per buffer. */
	static final int MAXIMUM_BUFFER_SIZE = 1 << 20;

	/** The number of pending operations per buffer before attempting to drain. */
	static final int BUFFER_THRESHOLD = 16;

	/** The number of buffers to use. */
	static final int NUMBER_OF_BUFFERS;

	/** Mask value for indexing into the buffers. */
	static final int BUFFER_MASK;

	/** The maximum number of operations to perform per amortized drain. */
	static final int AMORTIZED_DRAIN_THRESHOLD;

	/** A queue that discards all entries. */
	static final Queue<?> DISCARDING_QUEUE = new DiscardingQueue();

	static {
		final int buffers = ceilingNextPowerOfTwo(Runtime.getRuntime().availableProcessors());
		AMORTIZED_DRAIN_THRESHOLD = (1 + buffers) * BUFFER_THRESHOLD;
		NUMBER_OF_BUFFERS = buffers;
		BUFFER_MASK = buffers - 1;
	}

	static final int ceilingNextPowerOfTwo(final int x) {
		// From Hacker's Delight, Chapter 3, Harry S. Warren Jr.
		return 1 << Integer.SIZE - Integer.numberOfLeadingZeros(x - 1);
	}

	/** The draining status of the buffers. */
	enum DrainStatus {

		/** A drain is not taking place. */
		IDLE,

		/** A drain is required due to a pending write modification. */
		REQUIRED,

		/** A drain is in progress. */
		PROCESSING
	}

	// The backing data store holding the key-value associations
	final ConcurrentMap<K, Node> data;
	final int concurrencyLevel;

	// These fields provide support to bound the map by a maximum capacity
	final LinkedDeque<Node> evictionDeque;

	// must write under lock
	volatile int weightedSize;

	// must write under lock
	volatile int capacity;

	volatile int nextOrder;
	int drainedOrder;

	final Lock evictionLock;
	final Queue<Task>[] buffers;
	final ExecutorService executor;
	final Weigher<? super V> weigher;
	final AtomicIntegerArray bufferLengths;
	final AtomicReference<DrainStatus> drainStatus;

	// These fields provide support for notifying a listener.
	final Queue<Node> pendingNotifications;
	final EvictionListener<K, V> listener;

	transient Set<K> keySet;
	transient Collection<V> values;
	transient Set<Entry<K, V>> entrySet;

	/**
	 * Creates an instance based on the builder's configuration.
	 */
	@SuppressWarnings ({ "unchecked", "cast" })
	private ConcurrentLinkedHashMap(final Builder<K, V> builder) {
		// The data store and its maximum capacity
		concurrencyLevel = builder.concurrencyLevel;
		capacity = Math.min(builder.capacity, MAXIMUM_CAPACITY);
		data = new ConcurrentHashMap<>(builder.initialCapacity, 0.75f, concurrencyLevel);

		// The eviction support
		weigher = builder.weigher;
		executor = builder.executor;
		nextOrder = Integer.MIN_VALUE;
		drainedOrder = Integer.MIN_VALUE;
		evictionLock = new ReentrantLock();
		evictionDeque = new LinkedDeque<>();
		drainStatus = new AtomicReference<>(DrainStatus.IDLE);

		buffers = new Queue[NUMBER_OF_BUFFERS];
		bufferLengths = new AtomicIntegerArray(NUMBER_OF_BUFFERS);
		for (int i = 0; i < NUMBER_OF_BUFFERS; i++) {
			buffers[i] = new ConcurrentLinkedQueue<>();
		}

		// The notification queue and listener
		listener = builder.listener;
		pendingNotifications = listener == DiscardingListener.INSTANCE ? (Queue<Node>) DISCARDING_QUEUE
				: new ConcurrentLinkedQueue<>();
	}

	/** Asserts that the object is not null. */
	static void checkNotNull(final Object o) {
		if (o == null) { throw new NullPointerException(); }
	}

	/* ---------------- Eviction Support -------------- */

	/**
	 * Retrieves the maximum weighted capacity of the map.
	 * 
	 * @return the maximum weighted capacity
	 */
	public int capacity() {
		return capacity;
	}

	/**
	 * Sets the maximum weighted capacity of the map and eagerly evicts entries until it shrinks to the appropriate
	 * size.
	 * 
	 * @param capacity
	 *            the maximum weighted capacity of the map
	 * @throws IllegalArgumentException
	 *             if the capacity is negative
	 */
	public void setCapacity(final int capacity) {
		if (capacity < 0) { throw new IllegalArgumentException(); }

		evictionLock.lock();
		try {
			this.capacity = Math.min(capacity, MAXIMUM_CAPACITY);
			drainBuffers(AMORTIZED_DRAIN_THRESHOLD);
			evict();
		} finally {
			evictionLock.unlock();
		}
		notifyListener();
	}

	/** Determines whether the map has exceeded its capacity. */
	boolean hasOverflowed() {
		return weightedSize > capacity;
	}

	/**
	 * Evicts entries from the map while it exceeds the capacity and appends evicted entries to the notification queue
	 * for processing.
	 */
	void evict() {
		// Attempts to evict entries from the map if it exceeds the maximum
		// capacity. If the eviction fails due to a concurrent removal of the
		// victim, that removal may cancel out the addition that triggered this
		// eviction. The victim is eagerly unlinked before the removal task so
		// that if an eviction is still required then a new victim will be chosen
		// for removal.
		while (hasOverflowed()) {
			final Node node = evictionDeque.poll();

			// If weighted values are used, then the pending operations will adjust
			// the size to reflect the correct weight
			if (node == null) { return; }

			// Notify the listener only if the entry was evicted
			if (data.remove(node.key, node)) {
				pendingNotifications.add(node);
			}

			node.makeDead();
		}
	}

	/**
	 * Performs the post-processing work required after the map operation.
	 * 
	 * @param task
	 *            the pending operation to be applied
	 */
	void afterCompletion(final Task task) {
		final boolean delayable = schedule(task);
		if (shouldDrainBuffers(delayable)) {
			tryToDrainBuffers(AMORTIZED_DRAIN_THRESHOLD);
		}
		notifyListener();
	}

	/**
	 * Schedules the task to be applied to the page replacement policy.
	 * 
	 * @param task
	 *            the pending operation
	 * @return if the draining of the buffers can be delayed
	 */
	private boolean schedule(final Task task) {
		final int index = bufferIndex();
		final int buffered = bufferLengths.incrementAndGet(index);

		if (task.isWrite()) {
			buffers[index].add(task);
			drainStatus.set(DrainStatus.REQUIRED);
			return false;
		}

		// A buffer may discard a read task if its length exceeds a tolerance level
		if (buffered <= MAXIMUM_BUFFER_SIZE) {
			buffers[index].add(task);
			return buffered <= BUFFER_THRESHOLD;
		} else { // not optimized for fail-safe scenario
			bufferLengths.decrementAndGet(index);
			return false;
		}
	}

	/** Returns the index to the buffer that the task should be scheduled on. */
	static int bufferIndex() {
		// A buffer is chosen by the thread's id so that tasks are distributed in a
		// pseudo evenly manner. This helps avoid hot entries causing contention due
		// to other threads trying to append to the same buffer.
		return (int) Thread.currentThread().getId() & BUFFER_MASK;
	}

	/** Returns the ordering value to assign to a task. */
	int nextOrdering() {
		// The next ordering is acquired in a racy fashion as the increment is not
		// atomic with the insertion into a buffer. This means that concurrent tasks
		// can have the same ordering and the buffers are in a weakly sorted order.
		return nextOrder++;
	}

	/**
	 * Determines whether the buffers should be drained.
	 * 
	 * @param delayable
	 *            if a drain should be delayed until required
	 * @return if a drain should be attempted
	 */
	boolean shouldDrainBuffers(final boolean delayable) {
		if (executor.isShutdown()) {
			final DrainStatus status = drainStatus.get();
			return status != DrainStatus.PROCESSING & (!delayable | status == DrainStatus.REQUIRED);
		}
		return false;
	}

	/**
	 * Attempts to acquire the eviction lock and apply the pending operations to the page replacement policy.
	 * 
	 * @param maxToDrain
	 *            the maximum number of operations to drain
	 */
	void tryToDrainBuffers(final int maxToDrain) {
		if (evictionLock.tryLock()) {
			try {
				drainStatus.set(DrainStatus.PROCESSING);
				drainBuffers(maxToDrain);
			} finally {
				drainStatus.compareAndSet(DrainStatus.PROCESSING, DrainStatus.IDLE);
				evictionLock.unlock();
			}
		}
	}

	/**
	 * Drains the buffers and applies the pending operations.
	 * 
	 * @param maxToDrain
	 *            the maximum number of operations to drain
	 */
	void drainBuffers(final int maxToDrain) {
		// A mostly strict ordering is achieved by observing that each buffer
		// contains tasks in a weakly sorted order starting from the last drain.
		// The buffers can be merged into a sorted list in O(n) time by using
		// counting sort and chaining on a collision.

		// The output is capped to the expected number of tasks plus additional
		// slack to optimistically handle the concurrent additions to the buffers.
		final Task[] tasks = new Task[maxToDrain];

		// Moves the tasks into the output array, applies them, and updates the
		// marker for the starting order of the next drain.
		final int maxTaskIndex = moveTasksFromBuffers(tasks);
		runTasks(tasks, maxTaskIndex);
		updateDrainedOrder(tasks, maxTaskIndex);
	}

	/**
	 * Moves the tasks from the buffers into the output array.
	 * 
	 * @param tasks
	 *            the ordered array of the pending operations
	 * @return the highest index location of a task that was added to the array
	 */
	int moveTasksFromBuffers(final Task[] tasks) {
		int maxTaskIndex = -1;
		for (int i = 0; i < buffers.length; i++) {
			final int maxIndex = moveTasksFromBuffer(tasks, i);
			maxTaskIndex = Math.max(maxIndex, maxTaskIndex);
		}
		return maxTaskIndex;
	}

	/**
	 * Moves the tasks from the specified buffer into the output array.
	 * 
	 * @param tasks
	 *            the ordered array of the pending operations
	 * @param bufferIndex
	 *            the buffer to drain into the tasks array
	 * @return the highest index location of a task that was added to the array
	 */
	int moveTasksFromBuffer(final Task[] tasks, final int bufferIndex) {
		// While a buffer is being drained it may be concurrently appended to. The
		// number of tasks removed are tracked so that the length can be decremented
		// by the delta rather than set to zero.
		final Queue<Task> buffer = buffers[bufferIndex];
		int removedFromBuffer = 0;

		Task task;
		int maxIndex = -1;
		while ((task = buffer.poll()) != null) {
			removedFromBuffer++;

			// The index into the output array is determined by calculating the offset
			// since the last drain
			final int index = task.getOrder() - drainedOrder;
			if (index < 0) {
				// The task was missed by the last drain and can be run immediately
				task.run();
			} else if (index >= tasks.length) {
				// Due to concurrent additions, the order exceeds the capacity of the
				// output array. It is added to the end as overflow and the remaining
				// tasks in the buffer will be handled by the next drain.
				maxIndex = tasks.length - 1;
				addTaskToChain(tasks, task, maxIndex);
				break;
			} else {
				maxIndex = Math.max(index, maxIndex);
				addTaskToChain(tasks, task, index);
			}
		}
		bufferLengths.addAndGet(bufferIndex, -removedFromBuffer);
		return maxIndex;
	}

	/**
	 * Adds the task as the head of the chain at the index location.
	 * 
	 * @param tasks
	 *            the ordered array of the pending operations
	 * @param task
	 *            the pending operation to add
	 * @param index
	 *            the array location
	 */
	void addTaskToChain(final Task[] tasks, final Task task, final int index) {
		task.setNext(tasks[index]);
		tasks[index] = task;
	}

	/**
	 * Runs the pending page replacement policy operations.
	 * 
	 * @param tasks
	 *            the ordered array of the pending operations
	 * @param maxTaskIndex
	 *            the maximum index of the array
	 */
	void runTasks(final Task[] tasks, final int maxTaskIndex) {
		for (int i = 0; i <= maxTaskIndex; i++) {
			runTasksInChain(tasks[i]);
		}
	}

	/**
	 * Runs the pending operations on the linked chain.
	 * 
	 * @param task
	 *            the first task in the chain of operations
	 */
	void runTasksInChain(Task task) {
		while (task != null) {
			final Task current = task;
			task = task.getNext();
			current.setNext(null);
			current.run();
		}
	}

	/**
	 * Updates the order to start the next drain from.
	 * 
	 * @param tasks
	 *            the ordered array of operations
	 * @param maxTaskIndex
	 *            the maximum index of the array
	 */
	void updateDrainedOrder(final Task[] tasks, final int maxTaskIndex) {
		if (maxTaskIndex >= 0) {
			final Task task = tasks[maxTaskIndex];
			drainedOrder = task.getOrder() + 1;
		}
	}

	/** Notifies the listener of entries that were evicted. */
	void notifyListener() {
		Node node;
		while ((node = pendingNotifications.poll()) != null) {
			listener.onEviction(node.key, node.getValue());
		}
	}

	/** Updates the node's location in the page replacement policy. */
	class ReadTask extends AbstractTask {

		final Node node;

		ReadTask(final Node node) {
			this.node = node;
		}

		@Override
		public void run() {
			// An entry may scheduled for reordering despite having been previously
			// removed. This can occur when the entry was concurrently read while a
			// writer was removing it. If the entry is no longer linked then it does
			// not need to be processed.
			if (evictionDeque.contains(node)) {
				evictionDeque.moveToBack(node);
			}
		}

		@Override
		public boolean isWrite() {
			return false;
		}
	}

	/** Adds the node to the page replacement policy. */
	final class AddTask extends AbstractTask {

		final Node node;
		final int weight;

		AddTask(final Node node, final int weight) {
			this.weight = weight;
			this.node = node;
		}

		@Override
		public void run() {
			weightedSize += weight;

			// ignore out-of-order write operations
			if (node.get().isAlive()) {
				evictionDeque.add(node);
				evict();
			}
		}

		@Override
		public boolean isWrite() {
			return true;
		}
	}

	/** Removes a node from the page replacement policy. */
	final class RemovalTask extends AbstractTask {

		final Node node;

		RemovalTask(final Node node) {
			this.node = node;
		}

		@Override
		public void run() {
			// add may not have been processed yet
			evictionDeque.remove(node);
			node.makeDead();
		}

		@Override
		public boolean isWrite() {
			return true;
		}
	}

	/** Updates the weighted size and evicts an entry on overflow. */
	final class UpdateTask extends ReadTask {

		final int weightDifference;

		public UpdateTask(final Node node, final int weightDifference) {
			super(node);
			this.weightDifference = weightDifference;
		}

		@Override
		public void run() {
			super.run();
			weightedSize += weightDifference;
			evict();
		}

		@Override
		public boolean isWrite() {
			return true;
		}
	}

	/* ---------------- Concurrent Map Support -------------- */

	@Override
	public boolean isEmpty() {
		return data.isEmpty();
	}

	@Override
	public int size() {
		return data.size();
	}

	/**
	 * Returns the weighted size of this map.
	 * 
	 * @return the combined weight of the values in this map
	 */
	public int weightedSize() {
		return Math.max(0, weightedSize);
	}

	@Override
	public void clear() {
		// The alternative is to iterate through the keys and call #remove(), which
		// adds unnecessary contention on the eviction lock and buffers.
		evictionLock.lock();
		try {
			Node node;
			while ((node = evictionDeque.poll()) != null) {
				data.remove(node.key, node);
				node.makeDead();
			}

			// Drain the buffers and run only the write tasks
			for (int i = 0; i < buffers.length; i++) {
				final Queue<Task> buffer = buffers[i];
				int removed = 0;
				Task task;
				while ((task = buffer.poll()) != null) {
					if (task.isWrite()) {
						task.run();
					}
					removed++;
				}
				bufferLengths.addAndGet(i, -removed);
			}
		} finally {
			evictionLock.unlock();
		}
	}

	@Override
	public boolean containsKey(final Object key) {
		return data.containsKey(key);
	}

	@Override
	public boolean containsValue(final Object value) {
		checkNotNull(value);

		for (final Node node : data.values()) {
			if (node.getValue().equals(value)) { return true; }
		}
		return false;
	}

	@Override
	public V get(final Object key) {
		final Node node = data.get(key);
		if (node == null) { return null; }
		afterCompletion(new ReadTask(node));
		return node.getValue();
	}

	@Override
	public V put(final K key, final V value) {
		return put(key, value, false);
	}

	@Override
	public V putIfAbsent(final K key, final V value) {
		return put(key, value, true);
	}

	/**
	 * Adds a node to the list and the data store. If an existing node is found, then its value is updated if allowed.
	 * 
	 * @param key
	 *            key with which the specified value is to be associated
	 * @param value
	 *            value to be associated with the specified key
	 * @param onlyIfAbsent
	 *            a write is performed only if the key is not already associated with a value
	 * @return the prior value in the data store or null if no mapping was found
	 */
	V put(final K key, final V value, final boolean onlyIfAbsent) {
		checkNotNull(value);

		final int weight = weigher.weightOf(value);
		final WeightedValue<V> weightedValue = new WeightedValue<>(value, weight);
		final Node node = new Node(key, weightedValue);

		for (;;) {
			final Node prior = data.putIfAbsent(node.key, node);
			if (prior == null) {
				afterCompletion(new AddTask(node, weight));
				return null;
			} else if (onlyIfAbsent) {
				afterCompletion(new ReadTask(prior));
				return prior.getValue();
			}
			for (;;) {
				final WeightedValue<V> oldWeightedValue = prior.get();
				if (!oldWeightedValue.isAlive()) {
					break;
				}

				if (prior.compareAndSet(oldWeightedValue, weightedValue)) {
					final int weightedDifference = weight - oldWeightedValue.weight;
					final Task task =
							weightedDifference == 0 ? new ReadTask(prior) : new UpdateTask(prior, weightedDifference);
							afterCompletion(task);
							return oldWeightedValue.value;
				}
			}
		}
	}

	@Override
	public V remove(final Object key) {
		final Node node = data.remove(key);
		if (node == null) { return null; }

		node.makeRetired();
		afterCompletion(new RemovalTask(node));
		return node.getValue();
	}

	@Override
	public boolean remove(final Object key, final Object value) {
		final Node node = data.get(key);
		if (node == null || value == null) { return false; }

		WeightedValue<V> weightedValue = node.get();
		for (;;) {
			if (weightedValue.hasValue(value)) {
				if (node.tryToRetire(weightedValue)) {
					if (data.remove(key, node)) {
						afterCompletion(new RemovalTask(node));
						return true;
					}
				} else {
					weightedValue = node.get();
					if (weightedValue.isAlive()) {
						// retry as an intermediate update may have replaced the value
						// with
						// an equal instance that has a different reference identity
						continue;
					}
				}
			}
			return false;
		}
	}

	@Override
	public V replace(final K key, final V value) {
		checkNotNull(value);

		final int weight = weigher.weightOf(value);
		final WeightedValue<V> weightedValue = new WeightedValue<>(value, weight);

		final Node node = data.get(key);
		if (node == null) { return null; }
		for (;;) {
			final WeightedValue<V> oldWeightedValue = node.get();
			if (!oldWeightedValue.isAlive()) { return null; }
			if (node.compareAndSet(oldWeightedValue, weightedValue)) {
				final int weightedDifference = weight - oldWeightedValue.weight;
				final Task task =
						weightedDifference == 0 ? new ReadTask(node) : new UpdateTask(node, weightedDifference);
						afterCompletion(task);
						return oldWeightedValue.value;
			}
		}
	}

	@Override
	public boolean replace(final K key, final V oldValue, final V newValue) {
		checkNotNull(oldValue);
		checkNotNull(newValue);

		final int weight = weigher.weightOf(newValue);
		final WeightedValue<V> newWeightedValue = new WeightedValue<>(newValue, weight);

		final Node node = data.get(key);
		if (node == null) { return false; }
		for (;;) {
			final WeightedValue<V> weightedValue = node.get();
			if (!weightedValue.isAlive() || !weightedValue.hasValue(oldValue)) { return false; }
			if (node.compareAndSet(weightedValue, newWeightedValue)) {
				final int weightedDifference = weight - weightedValue.weight;
				final Task task =
						weightedDifference == 0 ? new ReadTask(node) : new UpdateTask(node, weightedDifference);
						afterCompletion(task);
						return true;
			}
		}
	}

	@Override
	public Set<K> keySet() {
		final Set<K> ks = keySet;
		return ks == null ? (keySet = new KeySet()) : ks;
	}

	/**
	 * Returns a unmodifiable snapshot {@link Set} view of the keys contained in this map. The set's iterator returns
	 * the keys whose order of iteration is the ascending order in which its entries are considered eligible for
	 * retention, from the least-likely to be retained to the most-likely.
	 * <p>
	 * Beware that, unlike in {@link #keySet()}, obtaining the set is <em>NOT</em> a constant-time operation. Because of
	 * the asynchronous nature of the page replacement policy, determining the retention ordering requires a traversal
	 * of the keys.
	 * 
	 * @return an ascending snapshot view of the keys in this map
	 */
	public Set<K> ascendingKeySet() {
		return orderedKeySet(true, Integer.MAX_VALUE);
	}

	/**
	 * Returns an unmodifiable snapshot {@link Set} view of the keys contained in this map. The set's iterator returns
	 * the keys whose order of iteration is the ascending order in which its entries are considered eligible for
	 * retention, from the least-likely to be retained to the most-likely.
	 * <p>
	 * Beware that, unlike in {@link #keySet()}, obtaining the set is <em>NOT</em> a constant-time operation. Because of
	 * the asynchronous nature of the page replacement policy, determining the retention ordering requires a traversal
	 * of the keys.
	 * 
	 * @param limit
	 *            the maximum size of the returned set
	 * @return a ascending snapshot view of the keys in this map
	 * @throws IllegalArgumentException
	 *             if the limit is negative
	 */
	public Set<K> ascendingKeySetWithLimit(final int limit) {
		return orderedKeySet(true, limit);
	}

	/**
	 * Returns an unmodifiable snapshot {@link Set} view of the keys contained in this map. The set's iterator returns
	 * the keys whose order of iteration is the descending order in which its entries are considered eligible for
	 * retention, from the most-likely to be retained to the least-likely.
	 * <p>
	 * Beware that, unlike in {@link #keySet()}, obtaining the set is <em>NOT</em> a constant-time operation. Because of
	 * the asynchronous nature of the page replacement policy, determining the retention ordering requires a traversal
	 * of the keys.
	 * 
	 * @return a descending snapshot view of the keys in this map
	 */
	public Set<K> descendingKeySet() {
		return orderedKeySet(false, Integer.MAX_VALUE);
	}

	/**
	 * Returns an unmodifiable snapshot {@link Set} view of the keys contained in this map. The set's iterator returns
	 * the keys whose order of iteration is the descending order in which its entries are considered eligible for
	 * retention, from the most-likely to be retained to the least-likely.
	 * <p>
	 * Beware that, unlike in {@link #keySet()}, obtaining the set is <em>NOT</em> a constant-time operation. Because of
	 * the asynchronous nature of the page replacement policy, determining the retention ordering requires a traversal
	 * of the keys.
	 * 
	 * @param limit
	 *            the maximum size of the returned set
	 * @return a descending snapshot view of the keys in this map
	 * @throws IllegalArgumentException
	 *             if the limit is negative
	 */
	public Set<K> descendingKeySetWithLimit(final int limit) {
		return orderedKeySet(false, limit);
	}

	Set<K> orderedKeySet(final boolean ascending, final int limit) {
		if (limit < 0) { throw new IllegalArgumentException(); }
		evictionLock.lock();
		try {
			drainBuffers(AMORTIZED_DRAIN_THRESHOLD);

			final int initialCapacity = weigher == Weighers.singleton() ? Math.min(limit, weightedSize()) : 16;
			final Set<K> keys = new LinkedHashSet<>(initialCapacity);
			final Iterator<Node> iterator = ascending ? evictionDeque.iterator() : evictionDeque.descendingIterator();
			while (iterator.hasNext() && limit > keys.size()) {
				keys.add(iterator.next().key);
			}
			return Collections.unmodifiableSet(keys);
		} finally {
			evictionLock.unlock();
		}
	}

	@Override
	public Collection<V> values() {
		final Collection<V> vs = values;
		return vs == null ? (values = new Values()) : vs;
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		final Set<Entry<K, V>> es = entrySet;
		return es == null ? (entrySet = new EntrySet()) : es;
	}

	/**
	 * Returns an unmodifiable snapshot {@link Map} view of the mappings contained in this map. The map's collections
	 * return the mappings whose order of iteration is the ascending order in which its entries are considered eligible
	 * for retention, from the least-likely to be retained to the most-likely.
	 * <p>
	 * Beware that obtaining the mappings is <em>NOT</em> a constant-time operation. Because of the asynchronous nature
	 * of the page replacement policy, determining the retention ordering requires a traversal of the entries.
	 * 
	 * @return a ascending snapshot view of this map
	 */
	public Map<K, V> ascendingMap() {
		return orderedMap(true, Integer.MAX_VALUE);
	}

	/**
	 * Returns an unmodifiable snapshot {@link Map} view of the mappings contained in this map. The map's collections
	 * return the mappings whose order of iteration is the ascending order in which its entries are considered eligible
	 * for retention, from the least-likely to be retained to the most-likely.
	 * <p>
	 * Beware that obtaining the mappings is <em>NOT</em> a constant-time operation. Because of the asynchronous nature
	 * of the page replacement policy, determining the retention ordering requires a traversal of the entries.
	 * 
	 * @param limit
	 *            the maximum size of the returned map
	 * @return a ascending snapshot view of this map
	 * @throws IllegalArgumentException
	 *             if the limit is negative
	 */
	public Map<K, V> ascendingMapWithLimit(final int limit) {
		return orderedMap(true, limit);
	}

	/**
	 * Returns an unmodifiable snapshot {@link Map} view of the mappings contained in this map. The map's collections
	 * return the mappings whose order of iteration is the descending order in which its entries are considered eligible
	 * for retention, from the most-likely to be retained to the least-likely.
	 * <p>
	 * Beware that obtaining the mappings is <em>NOT</em> a constant-time operation. Because of the asynchronous nature
	 * of the page replacement policy, determining the retention ordering requires a traversal of the entries.
	 * 
	 * @return a descending snapshot view of this map
	 */
	public Map<K, V> descendingMap() {
		return orderedMap(false, Integer.MAX_VALUE);
	}

	/**
	 * Returns an unmodifiable snapshot {@link Map} view of the mappings contained in this map. The map's collections
	 * return the mappings whose order of iteration is the descending order in which its entries are considered eligible
	 * for retention, from the most-likely to be retained to the least-likely.
	 * <p>
	 * Beware that obtaining the mappings is <em>NOT</em> a constant-time operation. Because of the asynchronous nature
	 * of the page replacement policy, determining the retention ordering requires a traversal of the entries.
	 * 
	 * @param limit
	 *            the maximum size of the returned map
	 * @return a descending snapshot view of this map
	 * @throws IllegalArgumentException
	 *             if the limit is negative
	 */
	public Map<K, V> descendingMapWithLimit(final int limit) {
		return orderedMap(false, limit);
	}

	Map<K, V> orderedMap(final boolean ascending, final int limit) {
		if (limit < 0) { throw new IllegalArgumentException(); }
		evictionLock.lock();
		try {
			drainBuffers(AMORTIZED_DRAIN_THRESHOLD);

			final int initialCapacity = weigher == Weighers.singleton() ? Math.min(limit, weightedSize()) : 16;
			final Map<K, V> map = new LinkedHashMap<>(initialCapacity);
			final Iterator<Node> iterator = ascending ? evictionDeque.iterator() : evictionDeque.descendingIterator();
			while (iterator.hasNext() && limit > map.size()) {
				final Node node = iterator.next();
				map.put(node.key, node.getValue());
			}
			return Collections.unmodifiableMap(map);
		} finally {
			evictionLock.unlock();
		}
	}

	/** A value, its weight, and the entry's status. */
	static final class WeightedValue<V> {

		final int weight;
		final V value;

		WeightedValue(final V value, final int weight) {
			this.weight = weight;
			this.value = value;
		}

		boolean hasValue(final Object o) {
			return o == value || value.equals(o);
		}

		/**
		 * If the entry is available in the hash-table and page replacement policy.
		 */
		boolean isAlive() {
			return weight > 0;
		}

		/**
		 * If the entry was removed from the hash-table and is awaiting removal from the page replacement policy.
		 */
		boolean isRetired() {
			return weight < 0;
		}

		/**
		 * If the entry was removed from the hash-table and the page replacement policy.
		 */
		boolean isDead() {
			return weight == 0;
		}
	}

	/**
	 * A node contains the key, the weighted value, and the linkage pointers on the page-replacement algorithm's data
	 * structures.
	 */
	@SuppressWarnings ("serial")
	final class Node extends AtomicReference<WeightedValue<V>> implements Linked<Node> {

		final K key;

		Node prev;
		Node next;

		/** Creates a new, unlinked node. */
		Node(final K key, final WeightedValue<V> weightedValue) {
			super(weightedValue);
			this.key = key;
		}

		@Override
		public Node getPrevious() {
			return prev;
		}

		@Override
		public void setPrevious(final Node prev) {
			this.prev = prev;
		}

		@Override
		public Node getNext() {
			return next;
		}

		@Override
		public void setNext(final Node next) {
			this.next = next;
		}

		/** Retrieves the value held by the current <tt>WeightedValue</tt>. */
		V getValue() {
			return get().value;
		}

		/**
		 * Attempts to transition the node from the <tt>alive</tt> state to the <tt>retired</tt> state.
		 * 
		 * @param expect
		 *            the expected weighted value
		 * @return if successful
		 */
		boolean tryToRetire(final WeightedValue<V> expect) {
			if (expect.isAlive()) {
				final WeightedValue<V> retired = new WeightedValue<>(expect.value, -expect.weight);
				return compareAndSet(expect, retired);
			}
			return false;
		}

		/**
		 * Atomically transitions the node from the <tt>alive</tt> state to the <tt>retired</tt> state, if a valid
		 * transition.
		 */
		void makeRetired() {
			for (;;) {
				final WeightedValue<V> current = get();
				if (!current.isAlive()) { return; }
				final WeightedValue<V> retired = new WeightedValue<>(current.value, -current.weight);
				if (compareAndSet(current, retired)) { return; }
			}
		}

		/**
		 * Atomically transitions the node to the <tt>dead</tt> state and decrements the <tt>weightedSize</tt>.
		 */
		void makeDead() {
			for (;;) {
				final WeightedValue<V> current = get();
				final WeightedValue<V> dead = new WeightedValue<>(current.value, 0);
				if (compareAndSet(current, dead)) {
					weightedSize -= Math.abs(current.weight);
					return;
				}
			}
		}
	}

	/** An adapter to safely externalize the keys. */
	final class KeySet extends AbstractSet<K> {

		final ConcurrentLinkedHashMap<K, V> map = ConcurrentLinkedHashMap.this;

		@Override
		public int size() {
			return map.size();
		}

		@Override
		public void clear() {
			map.clear();
		}

		@Override
		public Iterator<K> iterator() {
			return new KeyIterator();
		}

		@Override
		public boolean contains(final Object obj) {
			return containsKey(obj);
		}

		@Override
		public boolean remove(final Object obj) {
			return map.remove(obj) != null;
		}

		@Override
		public Object[] toArray() {
			return map.data.keySet().toArray();
		}

		@Override
		public <T> T[] toArray(final T[] array) {
			return map.data.keySet().toArray(array);
		}
	}

	/** An adapter to safely externalize the key iterator. */
	final class KeyIterator implements Iterator<K> {

		final Iterator<K> iterator = data.keySet().iterator();
		K current;

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public K next() {
			current = iterator.next();
			return current;
		}

		@Override
		public void remove() {
			if (current == null) { throw new IllegalStateException(); }
			ConcurrentLinkedHashMap.this.remove(current);
			current = null;
		}
	}

	/** An adapter to safely externalize the values. */
	final class Values extends AbstractCollection<V> {

		@Override
		public int size() {
			return ConcurrentLinkedHashMap.this.size();
		}

		@Override
		public void clear() {
			ConcurrentLinkedHashMap.this.clear();
		}

		@Override
		public Iterator<V> iterator() {
			return new ValueIterator();
		}

		@Override
		public boolean contains(final Object o) {
			return containsValue(o);
		}
	}

	/** An adapter to safely externalize the value iterator. */
	final class ValueIterator implements Iterator<V> {

		final Iterator<Node> iterator = data.values().iterator();
		Node current;

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public V next() {
			current = iterator.next();
			return current.getValue();
		}

		@Override
		public void remove() {
			if (current == null) { throw new IllegalStateException(); }
			ConcurrentLinkedHashMap.this.remove(current.key);
			current = null;
		}
	}

	/** An adapter to safely externalize the entries. */
	final class EntrySet extends AbstractSet<Entry<K, V>> {

		final ConcurrentLinkedHashMap<K, V> map = ConcurrentLinkedHashMap.this;

		@Override
		public int size() {
			return map.size();
		}

		@Override
		public void clear() {
			map.clear();
		}

		@Override
		public Iterator<Entry<K, V>> iterator() {
			return new EntryIterator();
		}

		@Override
		public boolean contains(final Object obj) {
			if (!(obj instanceof Entry<?, ?>)) { return false; }
			final Entry<?, ?> entry = (Entry<?, ?>) obj;
			final Node node = map.data.get(entry.getKey());
			return node != null && node.getValue().equals(entry.getValue());
		}

		@Override
		public boolean add(final Entry<K, V> entry) {
			return map.putIfAbsent(entry.getKey(), entry.getValue()) == null;
		}

		@Override
		public boolean remove(final Object obj) {
			if (!(obj instanceof Entry<?, ?>)) { return false; }
			final Entry<?, ?> entry = (Entry<?, ?>) obj;
			return map.remove(entry.getKey(), entry.getValue());
		}
	}

	/** An adapter to safely externalize the entry iterator. */
	final class EntryIterator implements Iterator<Entry<K, V>> {

		final Iterator<Node> iterator = data.values().iterator();
		Node current;

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public Entry<K, V> next() {
			current = iterator.next();
			return new WriteThroughEntry(current);
		}

		@Override
		public void remove() {
			if (current == null) { throw new IllegalStateException(); }
			ConcurrentLinkedHashMap.this.remove(current.key);
			current = null;
		}
	}

	/** An entry that allows updates to write through to the map. */
	final class WriteThroughEntry extends SimpleEntry<K, V> {


		WriteThroughEntry(final Node node) {
			super(node.key, node.getValue());
		}

		@Override
		public V setValue(final V value) {
			put(getKey(), value);
			return super.setValue(value);
		}

		Object writeReplace() {
			return new SimpleEntry<>(this);
		}
	}

	/** A weigher that enforces that the weight falls within a valid range. */
	static final class BoundedWeigher<V> implements Weigher<V>, Serializable {

		final Weigher<? super V> weigher;

		BoundedWeigher(final Weigher<? super V> weigher) {
			checkNotNull(weigher);
			this.weigher = weigher;
		}

		@Override
		public int weightOf(final V value) {
			final int weight = weigher.weightOf(value);
			if (weight < 1 || weight > MAXIMUM_WEIGHT) { throw new IllegalArgumentException("invalid weight"); }
			return weight;
		}

		Object writeReplace() {
			return weigher;
		}
	}

	/** A task that catches up the page replacement policy. */
	static final class CatchUpTask implements Runnable {

		final WeakReference<ConcurrentLinkedHashMap<?, ?>> mapRef;

		CatchUpTask(final ConcurrentLinkedHashMap<?, ?> map) {
			this.mapRef = new WeakReference<>(map);
		}

		@Override
		public void run() {
			final ConcurrentLinkedHashMap<?, ?> map = mapRef.get();
			if (map == null) { throw new CancellationException(); }
			int pendingTasks = 0;
			for (int i = 0; i < map.buffers.length; i++) {
				pendingTasks += map.bufferLengths.get(i);
			}
			if (pendingTasks != 0) {
				map.tryToDrainBuffers(pendingTasks + BUFFER_THRESHOLD);
			}
		}
	}

	/** An executor that is always terminated. */
	static final class DisabledExecutorService extends AbstractExecutorService {

		@Override
		public boolean isShutdown() {
			return true;
		}

		@Override
		public boolean isTerminated() {
			return true;
		}

		@Override
		public void shutdown() {}

		@Override
		public List<Runnable> shutdownNow() {
			return Collections.emptyList();
		}

		@Override
		public boolean awaitTermination(final long timeout, final TimeUnit unit) {
			return true;
		}

		@Override
		public void execute(final Runnable command) {
			throw new RejectedExecutionException();
		}
	}

	/** A queue that discards all additions and is always empty. */
	static final class DiscardingQueue extends AbstractQueue<Object> {

		@Override
		public boolean add(final Object e) {
			return true;
		}

		@Override
		public boolean offer(final Object e) {
			return true;
		}

		@Override
		public Object poll() {
			return null;
		}

		@Override
		public Object peek() {
			return null;
		}

		@Override
		public int size() {
			return 0;
		}

		@Override
		public Iterator<Object> iterator() {
			return Collections.emptyList().iterator();
		}
	}

	/** A listener that ignores all notifications. */
	enum DiscardingListener implements EvictionListener<Object, Object> {
		INSTANCE;

		@Override
		public void onEviction(final Object key, final Object value) {}
	}

	/** An operation that can be lazily applied to the page replacement policy. */
	interface Task extends Runnable {

		/** The priority order. */
		int getOrder();

		/** If the task represents an add, modify, or remove operation. */
		boolean isWrite();

		/** Returns the next task on the link chain. */
		Task getNext();

		/** Sets the next task on the link chain. */
		void setNext(Task task);
	}

	/** A skeletal implementation of the <tt>Task</tt> interface. */
	abstract class AbstractTask implements Task {

		final int order;
		Task task;

		AbstractTask() {
			order = nextOrdering();
		}

		@Override
		public int getOrder() {
			return order;
		}

		@Override
		public Task getNext() {
			return task;
		}

		@Override
		public void setNext(final Task task) {
			this.task = task;
		}
	}

	/* ---------------- Serialization Support -------------- */

	static final long serialVersionUID = 1;

	Object writeReplace() {
		return new SerializationProxy<>(this);
	}

	private void readObject(final ObjectInputStream stream) throws InvalidObjectException {
		throw new InvalidObjectException("Proxy required");
	}

	/**
	 * A proxy that is serialized instead of the map. The page-replacement algorithm's data structures are not
	 * serialized so the deserialized instance contains only the entries. This is acceptable as caches hold transient
	 * data that is recomputable and serialization would tend to be used as a fast warm-up process.
	 */
	static final class SerializationProxy<K, V> implements Serializable {

		final EvictionListener<K, V> listener;
		final Weigher<? super V> weigher;
		final int concurrencyLevel;
		final Map<K, V> data;
		final int capacity;

		SerializationProxy(final ConcurrentLinkedHashMap<K, V> map) {
			concurrencyLevel = map.concurrencyLevel;
			data = new HashMap<>(map);
			capacity = map.capacity;
			listener = map.listener;
			weigher = map.weigher;
		}

		Object readResolve() {
			final ConcurrentLinkedHashMap<K, V> map = new Builder<K, V>().concurrencyLevel(concurrencyLevel)
					.maximumWeightedCapacity(capacity).listener(listener).weigher(weigher).build();
			map.putAll(data);
			return map;
		}

	}

	/* ---------------- Builder -------------- */

	/**
	 * A builder that creates {@link ConcurrentLinkedHashMap} instances. It provides a flexible approach for
	 * constructing customized instances with a named parameter syntax. It can be used in the following manner:
	 * 
	 * <pre>
	 * 
	 * {
	 * 	&#064;code ConcurrentMap&lt;Vertex, Set&lt;Edge&gt;&gt; graph = new Builder&lt;Vertex, Set&lt;Edge&gt;&gt;().maximumWeightedCapacity(5000)
	 * 			.weigher(Weighers.&lt;Edge&gt; set()).build();
	 * }
	 * </pre>
	 */
	public static final class Builder<K, V> {

		static final ExecutorService DEFAULT_EXECUTOR = new DisabledExecutorService();
		static final int DEFAULT_CONCURRENCY_LEVEL = 16;
		static final int DEFAULT_INITIAL_CAPACITY = 16;

		EvictionListener<K, V> listener;
		Weigher<? super V> weigher;

		ExecutorService executor;
		TimeUnit unit;
		long delay;

		int concurrencyLevel;
		int initialCapacity;
		int capacity;

		@SuppressWarnings ("unchecked")
		public Builder() {
			capacity = -1;
			executor = DEFAULT_EXECUTOR;
			weigher = Weighers.singleton();
			initialCapacity = DEFAULT_INITIAL_CAPACITY;
			concurrencyLevel = DEFAULT_CONCURRENCY_LEVEL;
			listener = (EvictionListener<K, V>) DiscardingListener.INSTANCE;
		}

		/**
		 * Specifies the initial capacity of the hash table (default <tt>16</tt>). This is the number of key-value pairs
		 * that the hash table can hold before a resize operation is required.
		 * 
		 * @param initialCapacity
		 *            the initial capacity used to size the hash table to accommodate this many entries.
		 * @throws IllegalArgumentException
		 *             if the initialCapacity is negative
		 */
		public Builder<K, V> initialCapacity(final int initialCapacity) {
			if (initialCapacity < 0) { throw new IllegalArgumentException(); }
			this.initialCapacity = initialCapacity;
			return this;
		}

		/**
		 * Specifies the maximum weighted capacity to coerce the map to and may exceed it temporarily.
		 * 
		 * @param capacity
		 *            the weighted threshold to bound the map by
		 * @throws IllegalArgumentException
		 *             if the maximumWeightedCapacity is negative
		 */
		public Builder<K, V> maximumWeightedCapacity(final int capacity) {
			if (capacity < 0) { throw new IllegalArgumentException(); }
			this.capacity = capacity;
			return this;
		}

		/**
		 * Specifies the estimated number of concurrently updating threads. The implementation performs internal sizing
		 * to try to accommodate this many threads (default <tt>16</tt>).
		 * 
		 * @param concurrencyLevel
		 *            the estimated number of concurrently updating threads
		 * @throws IllegalArgumentException
		 *             if the concurrencyLevel is less than or equal to zero
		 */
		public Builder<K, V> concurrencyLevel(final int concurrencyLevel) {
			if (concurrencyLevel <= 0) { throw new IllegalArgumentException(); }
			this.concurrencyLevel = concurrencyLevel;
			return this;
		}

		/**
		 * Specifies an optional listener that is registered for notification when an entry is evicted.
		 * 
		 * @param listener
		 *            the object to forward evicted entries to
		 * @throws NullPointerException
		 *             if the listener is null
		 */
		public Builder<K, V> listener(final EvictionListener<K, V> listener) {
			checkNotNull(listener);
			this.listener = listener;
			return this;
		}

		/**
		 * Specifies an algorithm to determine how many the units of capacity a value consumes. The default algorithm
		 * bounds the map by the number of key-value pairs by giving each entry a weight of <tt>1</tt>.
		 * 
		 * @param weigher
		 *            the algorithm to determine a value's weight
		 * @throws NullPointerException
		 *             if the weigher is null
		 */
		public Builder<K, V> weigher(final Weigher<? super V> weigher) {
			this.weigher = weigher == Weighers.singleton() ? Weighers.<V> singleton() : new BoundedWeigher<>(weigher);
			return this;
		}

		/**
		 * Specifies an executor for use in catching up the page replacement policy. The catch-up phase processes both
		 * updates to the retention ordering and writes that may trigger an eviction. The delay should be chosen
		 * carefully as the map will not automatically evict between executions.
		 * <p>
		 * If unspecified or the executor is shutdown, the catching up will be amortized on user threads during write
		 * operations (or during read operations, in the absence of writes).
		 * <p>
		 * A single-threaded {@link ScheduledExecutorService} should be sufficient for catching up the page replacement
		 * policy in many maps.
		 * 
		 * @param executor
		 *            the executor to schedule on
		 * @param delay
		 *            the delay between executions
		 * @param unit
		 *            the time unit of the delay parameter
		 * @throws NullPointerException
		 *             if the executor or time unit is null
		 * @throws IllegalArgumentException
		 *             if the delay is less than or equal to zero
		 */
		public Builder<K, V> catchup(final ScheduledExecutorService executor, final long delay, final TimeUnit unit) {
			if (delay <= 0) { throw new IllegalArgumentException(); }
			checkNotNull(executor);
			checkNotNull(unit);
			this.executor = executor;
			this.delay = delay;
			this.unit = unit;
			return this;
		}

		/**
		 * Creates a new {@link ConcurrentLinkedHashMap} instance.
		 * 
		 * @throws IllegalStateException
		 *             if the maximum weighted capacity was not set
		 * @throws RejectedExecutionException
		 *             if an executor was specified and the catch-up task cannot be scheduled for execution
		 */
		public ConcurrentLinkedHashMap<K, V> build() {
			if (capacity < 0) { throw new IllegalStateException(); }
			final ConcurrentLinkedHashMap<K, V> map = new ConcurrentLinkedHashMap<>(this);
			if (executor != DEFAULT_EXECUTOR) {
				final ScheduledExecutorService es = (ScheduledExecutorService) executor;
				es.scheduleWithFixedDelay(new CatchUpTask(map), delay, delay, unit);
			}
			return map;
		}
	}

	// a class similar to AbstractMap.SimpleEntry. Needed for JDK 5 compatibility. Java 6
	// exposes it to external users.
	static class SimpleEntry<K, V> implements Entry<K, V> {

		K key;
		V value;

		public SimpleEntry(final K key, final V value) {
			this.key = key;
			this.value = value;
		}

		public SimpleEntry(final Entry<K, V> e) {
			this.key = e.getKey();
			this.value = e.getValue();
		}

		@Override
		public K getKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}

		@Override
		public V setValue(final V value) {
			final V oldValue = this.value;
			this.value = value;
			return oldValue;
		}

		@Override
		public boolean equals(final Object o) {
			if (!(o instanceof Entry)) {
				return false;
			}
			final Entry<?, ?> e = (Entry<?, ?>) o;
			return eq(key, e.getKey()) && eq(value, e.getValue());
		}

		@Override
		public int hashCode() {
			return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
		}

		private static boolean eq(final Object o1, final Object o2) {
			return o1 == null ? o2 == null : o1.equals(o2);
		}
	}
}