package com.tcs.mobility.sf.lecton.testcaser.mockingbird.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.PlatformUI;

import com.tcs.mobility.sf.lecton.utility.logging.WSConsole;

public class MockClasses extends AbstractHandler {

	public static final int AST_LEVEL = AST.JLS3;

	private List<ICompilationUnit> javaClassesToMockList;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		System.out.println("Mock Classes selected");

		javaClassesToMockList = getJavaClassesToMockFromSelection();
		if (javaClassesToMockList == null) {
			return null;
		}

		for (ICompilationUnit javaClass : javaClassesToMockList) {
			System.out.println(javaClass.getElementName());

			try {
				Document document = new Document(javaClass.getSource());

				ASTParser parser = ASTParser.newParser(AST_LEVEL);
				parser.setSource(javaClass);
				CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

				AST ast = astRoot.getAST();
				ASTRewrite rewriter = ASTRewrite.create(ast);

				// Get packageName
				Name packageName = astRoot.getPackage().getName();
				System.out.println("Package Name : "+packageName.getFullyQualifiedName());

				// Get list of imports
				for (Object importObj : astRoot.imports()) {
					if(importObj instanceof ImportDeclaration){
						Name importName = ((ImportDeclaration) importObj).getName();
						System.out.println("Import Name : "+importName.getFullyQualifiedName());
					}
				}
				
				// Get the type Name
				Object type = astRoot.types().get(0);
				
				

			} catch (JavaModelException e) {
				WSConsole.e(e);
			}

		}

		return null;
	}

	/**
	 * Returns all the ICompilationUnits that has to be mocked
	 * 
	 * @return List of javaFilesToMock
	 */
	private List<ICompilationUnit> getJavaClassesToMockFromSelection() {

		IJavaElement element = getSelectionJavaElement();
		if (element == null) {
			return null;
		}

		List<ICompilationUnit> javaFilesToMockList = new ArrayList<ICompilationUnit>();

		// The selection can be a single java file or a package
		if (element instanceof ICompilationUnit) {
			javaFilesToMockList.add((ICompilationUnit) element);
		} else if (element instanceof IPackageFragment) {
			try {
				javaFilesToMockList.addAll(Arrays.asList(((IPackageFragment) element).getCompilationUnits()));
			} catch (JavaModelException e) {
				WSConsole.e(e);
			}
		}

		return javaFilesToMockList;
	}

	/**
	 * Returns the selected object
	 * 
	 * @return the JavaElement selected
	 */
	private IJavaElement getSelectionJavaElement() {
		final ISelectionService selectionService = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getSelectionService();
		final ISelection selection = selectionService.getSelection();
		if (selection instanceof IStructuredSelection) {
			final Object firstElement = ((IStructuredSelection) selection).getFirstElement();
			if (firstElement instanceof IJavaElement) {
				return (IJavaElement) firstElement;
			}
		}
		return null;
	}
}
