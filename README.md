ClassSupplier
===========

ClassSupplier is an Eclipse plug-in allowing you to create code programmatically.  

It generates source code from the model provided, exports binary, installs it into its own runtime, and then loads classes making available for client code's reflective access through modeled API.  

ClassSupplier is purposed for creation of application with domain model, that is editable by users (currently under construction).

But for now, here is how you can use ClassSupplier:  

    // Create blueprint model - dynamic EMF EPackage
    EPackage ePackage = EcoreFactory.eINSTANCE.createEPackage();
    ePackage.setName("library");
    ePackage.setNsPrefix("library");
    ePackage.setNsURI("http://library/1.0");
    EClass eClass = EcoreFactory.eINSTANCE.createEClass();
    eClass.setName("Book");
    EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
    eAttr.setName("pages");
    eAttr.setEType(EcorePackage.Literals.EINT);
    eClass.getEStructuralFeatures().add(eAttr);
    ePackage.getEClassifiers().add(eClass);

    // Acquire ClassSupplier OSGi service
    BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass())
                                .getBundleContext();
    ServiceReference<?> serviceReference = bundleContext
                              .getServiceReference(ClassSupplier.class);
    ClassSupplier classSupplier = (ClassSupplier) bundleContext
                              .getService(serviceReference);

    // Combine them
    EPackage jPackage = classSupplier.create(ePackage);
    
    ...
    // Use the generated model in runtime
    EClass jClass = (EClass) jPackage.getEClassifier(eClass.getName());
    EObject jObject = jPackage.getEFactoryInstance().create(jClass); 
    int pages = 500;
    EStructuralFeature jAttr = jClass.getEStructuralFeature(eAttr
        .getName());
    jObject.eSet(jAttr, pages);
    assertEquals(pages, jObject.eGet(jAttr));
    assertEquals(eClass.getName(), jObject.getClass().getSimpleName());  
        
Here is even more [code](/tests/org.enterprisedomain.tests/src/org/enterprisedomain/tests/TestEnterpriseDomain.java), where you can specify a method body and call it, or customize generation templates. 


Feedback
---------
If you have anything to suggest, please feel free to file an [issue](https://github.com/kyrillzotkin/ClassSupplier/issues). 


Development continues...
