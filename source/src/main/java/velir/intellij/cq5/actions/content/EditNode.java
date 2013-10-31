package velir.intellij.cq5.actions.content;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jdom.JDOMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import velir.intellij.cq5.jcr.model.VNode;
import velir.intellij.cq5.ui.NodeDialog;
import velir.intellij.cq5.util.PsiUtils;

import java.io.IOException;

public class EditNode extends JCRAction {
	private static final Logger log = LoggerFactory.getLogger(EditNode.class);


	/**
	 * returns the appropriate .content.xml file, or null if none exists
	 * @param psiElement
	 * @return
	 */
	private PsiFile getContentFile (PsiElement psiElement) {
		if (psiElement instanceof PsiFile) {
			PsiFile psiFile = (PsiFile) psiElement;
			if (PsiUtils.CONTENT_XML.equals(psiFile.getName())) {
				return psiFile;
			}
		} else if (psiElement instanceof PsiDirectory) {
			PsiDirectory psiDirectory = (PsiDirectory) psiElement;
			return psiDirectory.findFile(PsiUtils.CONTENT_XML);
		}

		// we did not find an appropriate .content.xml
		return null;
	}

	@Override
	public void update(AnActionEvent e) {
		final DataContext dataContext = e.getDataContext();
		final Presentation presentation = e.getPresentation();

		final PsiElement element = (PsiElement)dataContext.getData(LangDataKeys.PSI_ELEMENT.getName());

		boolean enabled = isJCREvent(e) && getContentFile(element) != null;

		presentation.setVisible(enabled);
		presentation.setEnabled(enabled);
	}

	@Override
	public void actionPerformed(AnActionEvent anActionEvent) {
		final Project project = anActionEvent.getData(PlatformDataKeys.PROJECT);
		DataContext dataContext = anActionEvent.getDataContext();
		IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
		Application application = ApplicationManager.getApplication();
		final PsiElement element = (PsiElement)dataContext.getData(LangDataKeys.PSI_ELEMENT.getName());
		final PsiFile psiFile = getContentFile(element);

		if (psiFile != null) {

			// load node from xml file
			VNode vNode = application.runReadAction(new Computable<VNode>() {
				public VNode compute() {
					try {
						String name = PsiUtils.unmungeNamespace(psiFile.getContainingDirectory().getName());
						return VNode.makeVNode(psiFile.getVirtualFile().getInputStream(), name);
					} catch (IOException ioe) {
						log.error("Could not read node xml", ioe);
						Messages.showMessageDialog(project, "Could not read node xml", "Error", Messages.getErrorIcon());
					} catch (JDOMException jde) {
						log.error("Could not read node xml", jde);
						Messages.showMessageDialog(project, "Could not read node xml", "Error", Messages.getErrorIcon());
					}
					return null;
				}
			});

			// create dialog from node
			NodeDialog nodeDialog = new NodeDialog(project, vNode, false);
			nodeDialog.show();

			// if OK, update node xml
			if (nodeDialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
				final VNode newNode = nodeDialog.getVNode();
				application.runWriteAction(new Runnable() {
					public void run() {
						try {
							PsiUtils.writeNodeContent(psiFile, newNode);
						} catch (IOException ioe) {
							Messages.showMessageDialog(project, "Could not write to content file", "Error", Messages.getErrorIcon());
						}
					}
				});
			}

		} else { // should not happen
			Messages.showMessageDialog(project, "Action performed on non-file element somehow", "Error", Messages.getErrorIcon());
		}
	}
}
