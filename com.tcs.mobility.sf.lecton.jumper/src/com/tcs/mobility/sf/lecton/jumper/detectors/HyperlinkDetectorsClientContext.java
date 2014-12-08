package com.tcs.mobility.sf.lecton.jumper.detectors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PlatformUI;

import com.tcs.mobility.sf.lecton.jumper.hyperlinks.AbstractHyperlink;
import com.tcs.mobility.sf.lecton.jumper.hyperlinks.ClientContextHyperlink;
import com.tcs.mobility.sf.lecton.jumper.hyperlinks.ConfigHyperlink;
import com.tcs.mobility.sf.lecton.utility.logging.WSConsole;

public class HyperlinkDetectorsClientContext extends AbstractHyperlinkDetector {

	private static final Pattern patternConnectorRef = Pattern.compile(AbstractHyperlink.SF_CONNECTOR_REF);

	public HyperlinkDetectorsClientContext() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		IDocument document = textViewer.getDocument();
		int offset = region.getOffset();

		IFile file = ((IFileEditorInput) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor().getEditorInput())
				.getFile();
		IProject project = file.getProject();

		IRegion lineRegion = null;
		String matchLine = null;

		try {
			lineRegion = document.getLineInformationOfOffset(offset);
			matchLine = document.get(lineRegion.getOffset(), lineRegion.getLength());
		} catch (BadLocationException e) {
			WSConsole.e(e);
		}

		/*
		 * Search for the 'connector-ref'
		 */
		Matcher matcher = patternConnectorRef.matcher(matchLine);
		return createHyperlink(document, offset, project, lineRegion, matchLine, matcher, file.getFullPath());

	}

	private IHyperlink[] createHyperlink(IDocument document, int offset, IProject project, IRegion lineRegion, String matchLine, Matcher matcher,
			IPath filePath) {
		while (matcher.find()) {
			System.out.println("FOUND");

			String connectorId = matcher.group(1);
			System.out.println(connectorId);
			
			int index = matchLine.indexOf(connectorId);
			IRegion targetRegion = new Region(lineRegion.getOffset() + index, connectorId.length());
			if (targetRegion != null) {
				if ((targetRegion.getOffset() <= offset) && (targetRegion.getOffset() + targetRegion.getLength()) > offset) {
					IHyperlink hyperlink = new ClientContextHyperlink(targetRegion, connectorId, filePath, project);
					return new IHyperlink[] { hyperlink };
				}
			}
		}
		return null;
	}
}
