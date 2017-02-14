/**
 * Copyright 2012-2016 Kyrill Zotkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.enterprisedomain.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.enterprisedomain.classsupplier.ClassSupplier;
import org.enterprisedomain.classsupplier.CompletionListener;
import org.enterprisedomain.classsupplier.Contribution;
import org.enterprisedomain.classsupplier.Customizer;
import org.enterprisedomain.classsupplier.ModelPair;
import org.enterprisedomain.classsupplier.State;
import org.enterprisedomain.classsupplier.impl.CompletionListenerImpl;
import org.enterprisedomain.classsupplier.impl.CustomizerImpl;
import org.enterprisedomain.classsupplier.jobs.codegen.GenModelSetupJob;
import org.enterprisedomain.classsupplier.util.ModelUtil;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

public class TestEnterpriseDomain extends AbstractTest {

	private EObject o;
	private EPackage p;

	@Test
	public void creationOfTheClass() throws Exception {
		EcoreFactory ecoreFactory = EcoreFactory.eINSTANCE;
		final EPackage readerEPackage = createEPackage("reader", "1.0");
		final EClass eClass = ecoreFactory.createEClass();
		eClass.setName("Book");
		final EAttribute pagesAttr = ecoreFactory.createEAttribute();
		pagesAttr.setName("totalPages");
		pagesAttr.setEType(EcorePackage.Literals.EINT);
		eClass.getEStructuralFeatures().add(pagesAttr);

		final EAttribute attr = ecoreFactory.createEAttribute();
		attr.setName("pagesRead");
		attr.setEType(EcorePackage.Literals.EINT);
		eClass.getEStructuralFeatures().add(attr);

		final EOperation op = ecoreFactory.createEOperation();
		op.setName("read");
		EParameter p = ecoreFactory.createEParameter();
		p.setEType(EcorePackage.Literals.EINT);
		p.setName("pagesRead");
		op.getEParameters().add(p);
		EAnnotation an = ecoreFactory.createEAnnotation();
		an.setSource("http://www.eclipse.org/emf/2002/GenModel");
		an.getDetails().put("body", "setPagesRead(getPagesRead() + pagesRead);");
		op.getEAnnotations().add(an);
		EAnnotation invocation = ecoreFactory.createEAnnotation();
		invocation.setSource(ClassSupplier.INVOCATION_DELEGATE_URI);
		op.getEAnnotations().add(invocation);
		eClass.getEOperations().add(op);
		invocation = ecoreFactory.createEAnnotation();
		invocation.setSource(EcorePackage.eNS_URI);
		invocation.getDetails().put("invocationDelegates", ClassSupplier.INVOCATION_DELEGATE_URI);
		readerEPackage.getEAnnotations().add(invocation);
		readerEPackage.getEClassifiers().add(eClass);

		assertNotNull(service);

		final Contribution contribution = service.getWorkspace().createContribution(readerEPackage,
				getProgressMonitor());
		final Semaphore complete = new Semaphore(0);
		CompletionListener resultListener = new CompletionListenerImpl() {

			@Override
			public void completed(Contribution result) throws Exception {
				try {
					ModelPair ePackages = result.getDomainModel();
					assumeNotNull(ePackages);
					assertFalse(ePackages.getGenerated() == null);
					assertTrue(ModelUtil.ePackagesAreEqual(contribution.getDomainModel().getGenerated(),
							ePackages.getGenerated(), true));
					EPackage ePackage = ePackages.getGenerated();
					EClass theClass = (EClass) ePackage.getEClassifier(eClass.getName());
					EObject theObject = ePackage.getEFactoryInstance().create(theClass);

					int pages = 22;
					EAttribute objectPageAttr = (EAttribute) theClass.getEStructuralFeature(pagesAttr.getName());
					theObject.eSet(objectPageAttr, pages);
					assertEquals(pages, theObject.eGet(objectPageAttr));

					int readPagesCount = 11;
					EList<?> arguments = ECollections.asEList(readPagesCount);
					for (EOperation operation : theClass.getEAllOperations())
						if (operation.getName().equals(op.getName())) {
							EcoreUtil.getInvocationDelegateFactory(operation).createInvocationDelegate(operation)
									.dynamicInvoke((InternalEObject) theObject, arguments);
						}

					EStructuralFeature state = theClass.getEStructuralFeature(attr.getName());
					assertEquals(readPagesCount, theObject.eGet(state));

					assertEquals(eClass.getName(), theObject.getClass().getSimpleName());
				} catch (Exception e) {
					fail(e.getLocalizedMessage());
				} finally {
					complete.release();
				}
			}

		};
		contribution.addSaveCompletionListener(resultListener);
		contribution.save(getProgressMonitor());
		complete.acquire();
		contribution.removeSaveCompletionListener(resultListener);
	}

	@Test
	public void osgiService() throws Exception {
		BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
		ServiceReference<?> serviceReference = bundleContext.getServiceReference(ClassSupplier.class);
		ClassSupplier tested = (ClassSupplier) bundleContext.getService(serviceReference);
		assertNotNull(tested);
		EPackage ePackage = createEPackage("deeds", "0.2");
		EClass eClass = EcoreFactory.eINSTANCE.createEClass();
		final String className0 = "Hobby";
		eClass.setName(className0);
		ePackage.getEClassifiers().add(eClass);
		final String className1 = "Work";
		eClass = EcoreFactory.eINSTANCE.createEClass();
		eClass.setName(className1);
		ePackage.getEClassifiers().add(eClass);

		final Contribution contribution = tested.getWorkspace().createContribution(ePackage, getProgressMonitor());
		final Semaphore complete = new Semaphore(0);
		CompletionListener resultListener = new CompletionListenerImpl() {

			@Override
			public void completed(Contribution result) throws Exception {
				try {
					EPackage resultEPackage = result.getDomainModel().getGenerated();
					assumeNotNull(resultEPackage);
					assertObjectClass(className0, resultEPackage);
					assertObjectClass(className1, resultEPackage);
				} catch (Exception e) {
					fail(e.getLocalizedMessage());
				} finally {
					complete.release();
				}
			}
		};
		contribution.addSaveCompletionListener(resultListener);
		contribution.save(getProgressMonitor());
		complete.acquire();
		contribution.removeSaveCompletionListener(resultListener);

	}

	@Test
	public void metaModel() throws Exception {
		EcoreFactory factory = EcoreFactory.eINSTANCE;
		EPackage _package = createEPackage("meta", "");
		EClass metaClass = factory.createEClass();
		metaClass.setName("MetaObject");
		EAttribute nameAttribute = factory.createEAttribute();
		nameAttribute.setName("name");
		nameAttribute.setEType(EcorePackage.Literals.ESTRING);
		metaClass.getEStructuralFeatures().add(nameAttribute);
		EAttribute valueAttribute = factory.createEAttribute();
		valueAttribute.setName("value");
		valueAttribute.setEType(EcorePackage.Literals.EJAVA_OBJECT);
		metaClass.getEStructuralFeatures().add(valueAttribute);
		EOperation op = factory.createEOperation();
		op.setName("createInstance");
		op.setEType(metaClass);
		EAnnotation an = factory.createEAnnotation();
		an.setSource("http://www.eclipse.org/emf/2002/GenModel");
		an.getDetails().put("body", "return <%meta.MetaFactory%>.eINSTANCE.createMetaObject();");
		op.getEAnnotations().add(an);
		EAnnotation invocation = factory.createEAnnotation();
		invocation.setSource(ClassSupplier.INVOCATION_DELEGATE_URI);
		op.getEAnnotations().add(invocation);
		metaClass.getEOperations().add(op);
		_package.getEClassifiers().add(metaClass);
		EAnnotation invocationDelegate = factory.createEAnnotation();
		invocationDelegate.setSource(EcorePackage.eNS_URI);
		invocationDelegate.getDetails().put("invocationDelegates", ClassSupplier.INVOCATION_DELEGATE_URI);
		_package.getEAnnotations().add(invocationDelegate);

		EPackage ePackage = service.create(_package);
		assertNotNull(ePackage);
		EClass resultClass = (EClass) ePackage.getEClassifier(metaClass.getName());
		EObject metaObject = ePackage.getEFactoryInstance().create(resultClass);
		EAttribute resultAttribute = (EAttribute) resultClass.getEStructuralFeature(nameAttribute.getName());
		metaObject.eSet(resultAttribute, "Notebook");
		assertEquals("Notebook", metaObject.eGet(resultAttribute));
		EObject object = ePackage.getEFactoryInstance().create(resultClass);
		EList<?> arguments = ECollections.emptyEList();
		EObject nativeObject = null;
		for (EOperation operation : resultClass.getEAllOperations())
			if (operation.getName().equals(op.getName())) {
				nativeObject = (EObject) EcoreUtil.getInvocationDelegateFactory(operation)
						.createInvocationDelegate(operation).dynamicInvoke((InternalEObject) object, arguments);
			}
		assertEquals(object.eClass(), nativeObject.eClass());
	}

	@Test
	public void update() throws OperationCanceledException, InterruptedException, CoreException, ExecutionException {
		setClassName("Same");
		EcoreFactory f = EcoreFactory.eINSTANCE;
		p = createEPackage("updateable", "0.1");
		final EClass cl = f.createEClass();
		cl.setName(getClassName());
		setAttrName("a");
		final EAttribute a = f.createEAttribute();
		a.setName(getAttrName());
		a.setEType(EcorePackage.Literals.EJAVA_OBJECT);
		cl.getEStructuralFeatures().add(a);
		p.getEClassifiers().add(cl);

		final Contribution c = service.getWorkspace().createContribution(p, getProgressMonitor());
		final Semaphore complete = new Semaphore(0);

		CompletionListener l1 = new CompletionListenerImpl() {

			@Override
			public void completed(Contribution result) throws Exception {
				try {
					EPackage e0 = result.getDomainModel().getGenerated();
					EClass cla = (EClass) e0.getEClassifier(cl.getName());
					o = e0.getEFactoryInstance().create(cla);
					assertEquals(cla.getName(), o.getClass().getSimpleName());
					o.eSet(cla.getEStructuralFeature(a.getName()), "test");
					assertEquals("test", o.eGet(cla.getEStructuralFeature(a.getName())));
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					complete.release();
				}
			}
		};
		c.addSaveCompletionListener(l1);
		c.save(getProgressMonitor());
		complete.acquire();
		c.removeSaveCompletionListener(l1);

		final Version v1 = c.getVersion();

		final Registry packageRegistry = service.getWorkspace().getResourceSet().getPackageRegistry();
		assertNotNull(packageRegistry.getEPackage(p.getNsURI()));

		EPackage p2 = updateEPackage(EcoreUtil.copy(p), "0.2");
		final EAttribute b = f.createEAttribute();
		setAttrName("b");
		b.setName(getAttrName());
		b.setEType(EcorePackage.Literals.EINT);
		((EClass) p2.getEClassifier(cl.getName())).getEStructuralFeatures().add(b);
		EPackage e1 = service.update(p, p2, true);
		EClass cla = (EClass) e1.getEClassifier(cl.getName());
		o = e1.getEFactoryInstance().create(cla);
		assertEquals(cla.getName(), o.getClass().getSimpleName());
		o.eSet(cla.getEStructuralFeature(a.getName()), "test");
		assertEquals("test", o.eGet(cla.getEStructuralFeature(a.getName())));
		o.eSet(cla.getEStructuralFeature(b.getName()), 5);
		assertEquals(5, o.eGet(cla.getEStructuralFeature(b.getName())));
		Version v2 = c.getVersion();
		assertTrue(v2.compareTo(v1) > 0);

		assertNotNull(packageRegistry.getEPackage(p2.getNsURI()));

		c.checkout(v1);
		p = updateEPackage(p, "0.3");
		c.getDomainModel().setDynamic(p);

		CompletionListener l3 = new CompletionListenerImpl() {

			@Override
			public void completed(Contribution result) throws Exception {
				try {
					EPackage e2 = result.getDomainModel().getGenerated();
					assertEquals("http://" + e2.getName() + "/0.3", e2.getNsURI());
				} catch (Exception e) {
					fail(e.getLocalizedMessage());
				} finally {
					complete.release();
				}
			}
		};
		c.addSaveCompletionListener(l3);
		c.save(getProgressMonitor());
		complete.acquire();
		c.removeSaveCompletionListener(l3);

	}

	@Test
	public void downgrade() throws OperationCanceledException, InterruptedException, ExecutionException, CoreException {
		setPackageName("pack");
		setClassName("C");
		setAttrName("x");
		setAttrType(EcorePackage.Literals.EJAVA_OBJECT);
		Contribution contribution = createAndTestEPackage();
		Version v0 = contribution.getVersion();
		EPackage p = EcoreUtil.copy(contribution.getDomainModel().getDynamic());

		Version newVersion = contribution.newVersion(v0, false, false, true);
		contribution.newRevision(newVersion);
		contribution.checkout(newVersion);
		EClass clazz = (EClass) p.getEClassifier(getClassName());
		clazz.getEStructuralFeatures().remove(clazz.getEStructuralFeature(getAttrName()));
		p = updateEPackage(p, "1");
		contribution.getDomainModel().setDynamic(p);
		saveAndTest(contribution);
		EPackage g = contribution.getDomainModel().getGenerated();
		EClass gClazz = (EClass) g.getEClassifier(getClassName());
		EObject o = g.getEFactoryInstance().create(gClazz);
		assertNull(gClazz.getEStructuralFeature(getAttrName()));
		assertEquals(getClassName(), o.getClass().getSimpleName());

		contribution.checkout(v0);
		saveAndTest(contribution);
		g = contribution.getDomainModel().getGenerated();
		gClazz = (EClass) g.getEClassifier(getClassName());
		o = g.getEFactoryInstance().create(gClazz);
		EAttribute a = (EAttribute) gClazz.getEStructuralFeature(getAttrName());
		assertNotNull(a);
		assertEquals(getAttrType(), a.getEType());
		assertEquals(getClassName(), o.getClass().getSimpleName());
	}

	@Test
	public void recreate() throws CoreException, OperationCanceledException, InterruptedException, ExecutionException {
		setPackageName("p");
		setClassName("C");
		setAttrName("c");
		setAttrType(EcorePackage.Literals.EJAVA_OBJECT);
		Contribution c = createAndTestEPackage();
		c.delete(getProgressMonitor());
		createAndTestEPackage();
	}

	@Test
	public void version() throws OperationCanceledException, InterruptedException, ExecutionException, CoreException {
		setPackageName("some");
		setClassName("C");
		setAttrName("c");
		setAttrType(EcorePackage.Literals.EJAVA_OBJECT);
		Contribution c = createAndTestEPackage();
		Version oldVersion = c.getRevision().getVersion();
		Version version = c.nextVersion();
		c.newRevision(version);
		c.checkout(version);
		saveAndTest(c);
		c.checkout(oldVersion);
		saveAndTest(c);
	}

	@Test
	public void renameEPackage()
			throws OperationCanceledException, InterruptedException, ExecutionException, CoreException {
		setPackageName("one");
		setClassName("T");
		setAttrName("t");
		setAttrType(EcorePackage.Literals.EJAVA_OBJECT);
		EPackage p = createAndTestAPI();
		setPackageName("another");
		EPackage p2 = updateEPackage(EcoreUtil.copy(p), "1");
		p2.setName("another");
		p2.setNsPrefix("another");
		EClass cl = (EClass) p2.getEClassifier(getClassName());
		setClassName("P");
		cl.setName(getClassName());
		cl.getEStructuralFeature(getAttrName());
		testAPIUpdate(p, p2);
	}

	@Test
	public void immutable() throws NoSuchMethodException, CoreException, OperationCanceledException,
			InterruptedException, SecurityException, ClassNotFoundException, ExecutionException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException {
		setPackageName("immutable");
		EPackage eP = createEPackage(getPackageName(), "0.0.1");
		EClass eC = createEClass(getClassName());
		setAttrName("C");
		setAttrType(EcorePackage.Literals.EJAVA_OBJECT);
		EAttribute at = createEAttribute(getAttrName(), getAttrType());
		EcoreUtil.setSuppressedVisibility(at, EcoreUtil.SET, true);
		eC.getEStructuralFeatures().add(at);
		eP.getEClassifiers().add(eC);
		Contribution c = null;
		c = service.getWorkspace().createContribution(eP, getProgressMonitor());
		Customizer customizer = new CustomizerImpl() {

			@Override
			public void customize(EList<Object> args) {
				GenModel genModel = ((GenModel) args.get(1));
				genModel.setDynamicTemplates(true);
				genModel.setTemplateDirectory("platform:/plugin/org.genericdomain.tests/templates");
				genModel.setSuppressInterfaces(false);
			}

		};
		c.getCustomizers().put(GenModelSetupJob.STAGE, customizer);

		final Semaphore complete = new Semaphore(0);

		CompletionListener l = new CompletionListenerImpl() {

			@Override
			public void completed(Contribution result) throws Exception {
				try {
					EPackage e = null;
					Class<?> cl = null;
					try {
						e = result.getDomainModel().getGenerated();
						cl = e.getClass().getClassLoader().loadClass(getPackageName() + "." + getClassName());
						cl.getMethod("set" + getAttrName(), Object.class);
					} catch (NoSuchMethodException ex) {
					}
					Method m = cl.getMethod("get" + getAttrName(), new Class<?>[] {});
					EClass ec = (EClass) e.getEClassifier(getClassName());
					EObject eo = e.getEFactoryInstance().create(ec);
					Object o = new Object();
					eo.eSet(ec.getEStructuralFeature(getAttrName()), o);
					assertEquals(o, m.invoke(eo, new Object[] {}));
				} catch (Exception e) {
					fail(e.getLocalizedMessage());
				} finally {
					complete.release();
				}
			}
		};
		c.addSaveCompletionListener(l);
		c.save(getProgressMonitor());
		complete.acquire();
		c.removeSaveCompletionListener(l);
	}

	@Test
	public void package_() throws OperationCanceledException, InterruptedException, ExecutionException, CoreException {
		setPackageName("package");
		createAndTestEPackage();
	}
}