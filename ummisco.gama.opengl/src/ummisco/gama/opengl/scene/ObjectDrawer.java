/*********************************************************************************************
 *
 * 'ObjectDrawer.java, in plugin ummisco.gama.opengl, is part of the source code of the GAMA modeling and simulation
 * platform. (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package ummisco.gama.opengl.scene;

import msi.gama.common.geometry.AxisAngle;
import msi.gama.common.geometry.Envelope3D;
import msi.gama.common.geometry.Scaling3D;
import msi.gama.metamodel.shape.GamaPoint;
import ummisco.gama.opengl.JOGLRenderer;

public abstract class ObjectDrawer<T extends AbstractObject> {

	final OpenGL gl;

	public ObjectDrawer(final JOGLRenderer r) {
		gl = r.getOpenGLHelper();
	}

	void draw(final T object) {
		gl.beginObject(object);
		_draw(object);
		gl.disableTextures();
	}

	/**
	 * Applies a scaling to the gl context if a size is defined. The scaling is done with respect of the envelope of the
	 * geometrical object
	 * 
	 * @param object
	 *            the object defining the size and the original envelope of the geometry
	 * @param returns
	 *            true if a scaling occured, false otherwise
	 */
	protected boolean applyScaling(final AbstractObject object) {

		final Scaling3D size = object.getDimensions();
		if (size != null) {
			final Envelope3D env = object.getEnvelope(gl);
			if (env != null) {
				final boolean in2D =
						env.isFlat() || size.getZ() == 0d || object.getFile() != null && object.getFile().is2D();
				double factor = 0.0;
				if (in2D) {
					factor = Math.min(size.getX() / env.getWidth(), size.getY() / env.getHeight());
				} else {
					final double min_xy = Math.min(size.getX() / env.getWidth(), size.getY() / env.getHeight());
					factor = Math.min(min_xy, size.getZ() / env.getDepth());
				}
				if (factor != 1d) {
					gl.scaleBy(factor, factor, factor);
				}
				return true;
			}
		}
		return false;

	}

	/**
	 * Applies either the rotation defined by the modeler in the draw statement and/or the initial rotation imposed to
	 * geometries read from 3D files to the gl context
	 * 
	 * @param object
	 *            the object specifying the rotations
	 * @return true if one of the 2 rotations is applied, false otherwise
	 */
	protected boolean applyRotation(final AbstractObject object) {
		final AxisAngle rotation = object.getRotation();
		final AxisAngle initRotation = object.getInitRotation();
		if (rotation == null && initRotation == null) { return false; }
		final GamaPoint loc = object.getLocation();
		try {
			gl.translateBy(loc.x, -loc.y, loc.z);
			if (rotation != null) {
				final GamaPoint axis = rotation.getAxis();
				// AD Change to a negative rotation to fix Issue #1514
				gl.rotateBy(-rotation.getAngle(), axis.x, axis.y, axis.z);
			}
			if (initRotation != null) {
				final GamaPoint initAxis = initRotation.axis;
				// AD Change to a negative rotation to fix Issue #1514
				gl.rotateBy(-initRotation.angle, initAxis.x, initAxis.y, initAxis.z);
			}
		} finally {
			gl.translateBy(-loc.x, loc.y, -loc.z);
		}
		return true;
	}

	protected abstract void _draw(T object);

}
