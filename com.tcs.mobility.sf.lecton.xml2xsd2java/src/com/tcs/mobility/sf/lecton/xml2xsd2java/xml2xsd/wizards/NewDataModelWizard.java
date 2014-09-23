package com.tcs.mobility.sf.lecton.xml2xsd2java.xml2xsd.wizards;

import java.beans.Introspector;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.actions.OrganizeImportsAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.internal.intro.IntroMessages;
import org.eclipse.ui.progress.UIJob;

import com.tcs.mobility.sf.lecton.bttsource.models.context.elements.dataelements.KeyedCollectionModel;
import com.tcs.mobility.sf.lecton.utility.logging.WSConsole;
import com.tcs.mobility.sf.lecton.utility.utils.UtilResource;
import com.tcs.mobility.sf.lecton.xml2xsd.source.builder.XsdBuilder;
import com.tcs.mobility.sf.lecton.xml2xsd2java.listeners.JobChangeListener;
import com.tcs.mobility.sf.lecton.xml2xsd2java.xml2xsd.wizards.pages.PrimaryInputWizardPage;
import com.tcs.mobility.sf.lecton.xml2xsd2java.xml2xsd.wizards.pages.XMLInputWizardPage;
import com.tcs.mobility.sf.lecton.xml2xsd2java.xsd2java.controller.JavaGenerator;

public class NewDataModelWizard extends Wizard implements INewWizard {

	PrimaryInputWizardPage primaryInputWizardPage;
	XMLInputWizardPage xmlInputWizardPage;

	private IStructuredSelection selection;
	private IWorkbenchSite site;

	public NewDataModelWizard() {
		super();
		setWindowTitle("Create Data Model");
		setNeedsProgressMonitor(true);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.selection = selection;
		this.site = workbench.getActiveWorkbenchWindow().getActivePage().getActivePart().getSite();
	}

	@Override
	public boolean performFinish() {
		final String pkgPath = primaryInputWizardPage.getTxtRootFolder();
		final String className = primaryInputWizardPage.getTxtServiceNameText();
		final String commonPackageName = primaryInputWizardPage.getTxtCommonPackageName();
		final String modulePackageName = primaryInputWizardPage.getTxtModulePackageName();
		final String packageName = primaryInputWizardPage.getTxtTxtpackageName();
		final boolean isOnlyXSD = primaryInputWizardPage.isOnlyXsd();

		final KeyedCollectionModel requestKColl = xmlInputWizardPage.getRequestKColl();
		final KeyedCollectionModel responseKColl = xmlInputWizardPage.getResponseKColl();

		IRunnableWithProgress op = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				try {
					doFinish(className, pkgPath, commonPackageName, modulePackageName, packageName, isOnlyXSD, requestKColl, responseKColl, monitor);
				} catch (MalformedTreeException e) {
					e.printStackTrace();
				} catch (JavaModelException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CoreException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					monitor.done();
				}
			}
		};

		try {
			getContainer().run(true, false, op);
		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException e) {
			Throwable realException = e.getTargetException();
			e.printStackTrace();
			MessageDialog.openError(getShell(), "Error", realException.getMessage());
			return false;
		}

		return true;
	}

	protected void doFinish(String serviceName, String rootFolder, String commonPackageName, String modulePackageName, String packageName,
			boolean isOnlyXSD, KeyedCollectionModel requestKColl, KeyedCollectionModel responseKColl, IProgressMonitor monitor) throws CoreException {

		monitor.beginTask("Setting up the preliminaries", 3);

		IResource srcResource = UtilResource.getResourceHandle(rootFolder);

		// Create the src folder if not created
		if (!srcResource.exists()) {
			UtilResource.createFile(srcResource);
		}

		// Get reference to the Project
		IProject project = srcResource.getProject();

		String directoryPath = project.getLocation().append(JavaGenerator.PACKAGEROOT_SRC).toOSString();

		// Construct the XSD location
		IPath xsdLocationPath = new Path(project.getFullPath().toOSString()).append(JavaGenerator.PACKAGEROOT_RESOURCE).append(
				JavaGenerator.PACKAGEROOT_XSD_LOCATION);
		System.out.println(xsdLocationPath);
		IResource resResource = UtilResource.getResourceHandle(xsdLocationPath.toOSString());

		// Create the XSD location folder if not created
		if (!resResource.exists()) {
			UtilResource.createFile(resResource);
		}

		monitor.worked(1);
		monitor.setTaskName("Creating XSD files");

		// Create only the XSD file in the Resource folder.
		System.out.println("ONLY XSD - WORKING");

		// Create XSD
		IFile requestXsdFile = createXsdFile(serviceName, requestKColl, JavaGenerator.XSD_NAME_REQUEST, monitor, resResource);
		IFile responseXsdFile = createXsdFile(serviceName, responseKColl, JavaGenerator.XSD_NAME_RESPONSE, monitor, resResource);

		monitor.worked(1);
		monitor.setTaskName("Generating Java Class from XSD");
		if (!isOnlyXSD) {
			JavaGenerator javaGenerator = new JavaGenerator();
			// Create Jobs for Request and Response
			final Job requestJob = generateJavaJob("GenerateRequestJavaJob", javaGenerator, project, directoryPath, requestXsdFile, packageName,
					commonPackageName);
			final Job responseJob = generateJavaJob("GenerateResponseJavaJob", javaGenerator, project, directoryPath, responseXsdFile, packageName,
					commonPackageName);

			// Set listener to determine if 1st job is finished. Only then,
			// the 2nd job should be executed.
			final IJobManager manager = Job.getJobManager();
			final IJobChangeListener listener = new JobChangeListener() {

				@Override
				public void done(IJobChangeEvent event) {
					if (event.getJob().getName().equalsIgnoreCase("Assigning propOrder")) {
						responseJob.schedule();
						manager.removeJobChangeListener(this);
					}
				}

			};
			manager.addJobChangeListener(listener);
			requestJob.schedule();

			/*
			 * Create Module java file
			 */
			IJavaProject javaProject = JavaCore.create(project);
			IPackageFragmentRoot packageFragmentRoot = javaProject.getPackageFragmentRoot(srcResource);
			IPackageFragment modulePackage = packageFragmentRoot.getPackageFragment(modulePackageName);

			// Create package for service
			IFolder moduleFolder = (IFolder) modulePackage.getResource();
			IFolder serviceModuleFolder = moduleFolder.getFolder(Introspector.decapitalize(serviceName));
			serviceModuleFolder.getLocation().toFile().mkdirs();
			project.refreshLocal(IProject.DEPTH_INFINITE, monitor);

			IPackageFragment servicePackage = (IPackageFragment) JavaCore.create(serviceModuleFolder);
			String moduleName = serviceName + "Module";

			// Create the Module file
			final ICompilationUnit moduleUnit = servicePackage.createCompilationUnit(moduleName + JavaGenerator.EXTENSION_JAVA,
					getModuleContents(serviceName, serviceModuleFolder, moduleName, modulePackageName), true, monitor);

			
			// Organize imports for the Module file created
			Runnable job = new Runnable() {
				@Override
				public void run() {
					OrganizeImportsAction org = new OrganizeImportsAction(site);
					org.run(moduleUnit);
				}

			};
			getShell().getDisplay().syncExec(job);
		}
		monitor.worked(1);
	}

	private String getModuleContents(String serviceName, IFolder serviceModuleFolder, String moduleName, String modulePackageName) {
		StringWriter writer = new StringWriter();

		final String LINE_TERMINATION = ";\n";
		writer.append("package ").append(modulePackageName).append(".").append(serviceModuleFolder.getName()).append(LINE_TERMINATION);
		writer.append("public interface ").append(moduleName).append("{\n");
		writer.append("Response<").append(serviceName).append("Response> ").append(Introspector.decapitalize(serviceName)).append("(final ")
				.append(serviceName).append("Request ").append(Introspector.decapitalize(serviceName)).append("Request)").append(LINE_TERMINATION);
		writer.append("}\n");
		return writer.toString();
	}

	private Job generateJavaJob(final String jobName, final JavaGenerator javaGenerator, final IProject project, final String directoryPath,
			final IFile xsdFile, final String packageName, final String commonPackageName) {
		final Job job = new UIJob(jobName) {

			@Override
			public boolean belongsTo(Object family) {
				return (JavaGenerator.JOB_FAMILY_GENERATE_JAVA).equals(family);
			}

			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				System.out.println("GENERATE JAVA JOB - STARTING : " + jobName);
				WSConsole.i("Creating XSD Files - " + jobName);
				javaGenerator.generateJava(project, directoryPath, xsdFile.getLocation().toOSString(), packageName, commonPackageName, site, true);
				WSConsole.i("XSD Files created - " + jobName);
				System.out.println("GENERATE JAVA JOB - FINISHING : " + jobName);
				return Status.OK_STATUS;
			}
		};
		return job;
	}

	private IFile createXsdFile(String serviceName, KeyedCollectionModel rootKColl, String type, IProgressMonitor monitor, IResource resResource) {
		XsdBuilder builder = new XsdBuilder();
		String javaClassName = serviceName + type;
		rootKColl.setId(javaClassName);
		String requestContent = builder.makeXSD(rootKColl);

		IFile file = null;
		if (resResource instanceof IFolder) {
			try {
				file = ((IFolder) resResource).getFile(javaClassName + JavaGenerator.EXTENSION_XSD);
				InputStream inputStream = new ByteArrayInputStream(requestContent.getBytes("UTF-8"));
				file.create(inputStream, true, monitor);
			} catch (CoreException e) {
				WSConsole.e(e.getMessage());
				WSConsole.e(e);
			} catch (UnsupportedEncodingException e) {
				WSConsole.e(e.getMessage());
				WSConsole.e(e);
			}
		}
		return file;
	}

	@Override
	public void addPages() {
		primaryInputWizardPage = new PrimaryInputWizardPage(selection);
		addPage(primaryInputWizardPage);
		xmlInputWizardPage = new XMLInputWizardPage();
		addPage(xmlInputWizardPage);
	}

	@Override
	public boolean canFinish() {
		return (getContainer().getCurrentPage() == xmlInputWizardPage && xmlInputWizardPage.isPageComplete());

	}
}
