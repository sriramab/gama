/**
 * generated by Xtext
 */
package msi.gama.lang.gaml.gaml;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Headless Experiment</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getKey <em>Key</em>}</li>
 *   <li>{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getFirstFacet <em>First Facet</em>}</li>
 *   <li>{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getName <em>Name</em>}</li>
 *   <li>{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getImportURI <em>Import URI</em>}</li>
 *   <li>{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getFacets <em>Facets</em>}</li>
 *   <li>{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getBlock <em>Block</em>}</li>
 * </ul>
 *
 * @see msi.gama.lang.gaml.gaml.GamlPackage#getHeadlessExperiment()
 * @model
 * @generated
 */
public interface HeadlessExperiment extends EObject
{
  /**
   * Returns the value of the '<em><b>Key</b></em>' attribute.
   * <!-- begin-user-doc -->
   * <p>
   * If the meaning of the '<em>Key</em>' attribute isn't clear,
   * there really should be more of a description here...
   * </p>
   * <!-- end-user-doc -->
   * @return the value of the '<em>Key</em>' attribute.
   * @see #setKey(String)
   * @see msi.gama.lang.gaml.gaml.GamlPackage#getHeadlessExperiment_Key()
   * @model
   * @generated
   */
  String getKey();

  /**
   * Sets the value of the '{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getKey <em>Key</em>}' attribute.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @param value the new value of the '<em>Key</em>' attribute.
   * @see #getKey()
   * @generated
   */
  void setKey(String value);

  /**
   * Returns the value of the '<em><b>First Facet</b></em>' attribute.
   * <!-- begin-user-doc -->
   * <p>
   * If the meaning of the '<em>First Facet</em>' attribute isn't clear,
   * there really should be more of a description here...
   * </p>
   * <!-- end-user-doc -->
   * @return the value of the '<em>First Facet</em>' attribute.
   * @see #setFirstFacet(String)
   * @see msi.gama.lang.gaml.gaml.GamlPackage#getHeadlessExperiment_FirstFacet()
   * @model
   * @generated
   */
  String getFirstFacet();

  /**
   * Sets the value of the '{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getFirstFacet <em>First Facet</em>}' attribute.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @param value the new value of the '<em>First Facet</em>' attribute.
   * @see #getFirstFacet()
   * @generated
   */
  void setFirstFacet(String value);

  /**
   * Returns the value of the '<em><b>Name</b></em>' attribute.
   * <!-- begin-user-doc -->
   * <p>
   * If the meaning of the '<em>Name</em>' attribute isn't clear,
   * there really should be more of a description here...
   * </p>
   * <!-- end-user-doc -->
   * @return the value of the '<em>Name</em>' attribute.
   * @see #setName(String)
   * @see msi.gama.lang.gaml.gaml.GamlPackage#getHeadlessExperiment_Name()
   * @model
   * @generated
   */
  String getName();

  /**
   * Sets the value of the '{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getName <em>Name</em>}' attribute.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @param value the new value of the '<em>Name</em>' attribute.
   * @see #getName()
   * @generated
   */
  void setName(String value);

  /**
   * Returns the value of the '<em><b>Import URI</b></em>' attribute.
   * <!-- begin-user-doc -->
   * <p>
   * If the meaning of the '<em>Import URI</em>' attribute isn't clear,
   * there really should be more of a description here...
   * </p>
   * <!-- end-user-doc -->
   * @return the value of the '<em>Import URI</em>' attribute.
   * @see #setImportURI(String)
   * @see msi.gama.lang.gaml.gaml.GamlPackage#getHeadlessExperiment_ImportURI()
   * @model
   * @generated
   */
  String getImportURI();

  /**
   * Sets the value of the '{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getImportURI <em>Import URI</em>}' attribute.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @param value the new value of the '<em>Import URI</em>' attribute.
   * @see #getImportURI()
   * @generated
   */
  void setImportURI(String value);

  /**
   * Returns the value of the '<em><b>Facets</b></em>' containment reference list.
   * The list contents are of type {@link msi.gama.lang.gaml.gaml.Facet}.
   * <!-- begin-user-doc -->
   * <p>
   * If the meaning of the '<em>Facets</em>' containment reference list isn't clear,
   * there really should be more of a description here...
   * </p>
   * <!-- end-user-doc -->
   * @return the value of the '<em>Facets</em>' containment reference list.
   * @see msi.gama.lang.gaml.gaml.GamlPackage#getHeadlessExperiment_Facets()
   * @model containment="true"
   * @generated
   */
  EList<Facet> getFacets();

  /**
   * Returns the value of the '<em><b>Block</b></em>' containment reference.
   * <!-- begin-user-doc -->
   * <p>
   * If the meaning of the '<em>Block</em>' containment reference isn't clear,
   * there really should be more of a description here...
   * </p>
   * <!-- end-user-doc -->
   * @return the value of the '<em>Block</em>' containment reference.
   * @see #setBlock(Block)
   * @see msi.gama.lang.gaml.gaml.GamlPackage#getHeadlessExperiment_Block()
   * @model containment="true"
   * @generated
   */
  Block getBlock();

  /**
   * Sets the value of the '{@link msi.gama.lang.gaml.gaml.HeadlessExperiment#getBlock <em>Block</em>}' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @param value the new value of the '<em>Block</em>' containment reference.
   * @see #getBlock()
   * @generated
   */
  void setBlock(Block value);

} // HeadlessExperiment
