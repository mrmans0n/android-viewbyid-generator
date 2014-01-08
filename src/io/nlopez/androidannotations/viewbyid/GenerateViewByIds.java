package io.nlopez.androidannotations.viewbyid;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Created by mrm on 08/01/14.
 */
public class GenerateViewByIds extends BaseGenerateAction implements IConfirmListener, ICancelListener {

	protected JFrame dialog;

	public GenerateViewByIds() {
		super(null);
	}

	public GenerateViewByIds(CodeInsightActionHandler handler) {
		super(handler);
	}

	@Override
	protected boolean isValidForClass(final PsiClass targetClass) {
		PsiClass injectViewClass = JavaPsiFacade.getInstance(targetClass.getProject()).findClass("org.androidannotations.annotations.ViewById", new EverythingGlobalScope(targetClass.getProject()));

		return (injectViewClass != null && super.isValidForClass(targetClass) && Utils.findAndroidSDK() != null && !(targetClass instanceof PsiAnonymousClass));
	}

	@Override
	public boolean isValidForFile(Project project, Editor editor, PsiFile file) {
		PsiClass injectViewClass = JavaPsiFacade.getInstance(project).findClass("org.androidannotations.annotations.ViewById", new EverythingGlobalScope(project));

		return (injectViewClass != null && super.isValidForFile(project, editor, file) && Utils.getLayoutFileFromCaret(editor, file) != null);
	}

	@Override
	public void actionPerformed(AnActionEvent e) {
		Project project = e.getData(PlatformDataKeys.PROJECT);
		Editor editor = e.getData(PlatformDataKeys.EDITOR);

		actionPerformedImpl(project, editor);
	}

	@Override
	public void actionPerformedImpl(@NotNull Project project, Editor editor) {
		PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
		PsiFile layout = Utils.getLayoutFileFromCaret(editor, file);

		if (layout == null) {
			Utils.showErrorNotification(project, "No layout found - Please place the caret in the layout name");
			return;
		}

		ArrayList<Element> elements = Utils.getIDsFromLayout(layout);
		if (elements.size() > 0) {

			showDialog(project, editor, elements);

		} else {
			Utils.showErrorNotification(project, "No IDs found in the selected layout");
		}
	}

	protected void showDialog(Project project, Editor editor, ArrayList<Element> elements) {
		PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
		PsiClass clazz = getTargetClass(editor, file);

		// get already generated injections
		ArrayList<String> ids = new ArrayList<String>();
		PsiField[] fields = clazz.getAllFields();
		String[] annotations;
		String id;

		for (PsiField field : fields) {
			annotations = field.getFirstChild().getText().split(" ");

			for (String annotation : annotations) {
				id = Utils.getInjectionID(annotation.trim());
				if (!Utils.isEmptyString(id)) {
					ids.add(id);
				}
			}
		}

		EntryList panel = new EntryList(project, editor, elements, ids, this, this);

		dialog = new JFrame();
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.getContentPane().add(panel);
		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
	}

	@Override
	public void onCancel() {
		closeDialog();
	}

	protected void closeDialog() {
		if (dialog == null) {
			return;
		}

		dialog.setVisible(false);
		dialog.dispose();
	}

	@Override
	public void onConfirm(Project project, Editor editor, ArrayList<Element> elements, String fieldNamePrefix) {
		PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
		PsiFile layout = Utils.getLayoutFileFromCaret(editor, file);

		closeDialog();

		int cnt = 0;
		for (Element element : elements) {
			if (element.used) {
				cnt++;
			}
		}

		if (cnt > 0) { // generate injections
			new ViewByIdWriter(file, getTargetClass(editor, file), "Generate Injections", elements, fieldNamePrefix, layout.getName()).execute();

			if (cnt == 1) {
				Utils.showInfoNotification(project, "One injection added to " + file.getName());
			} else {
				Utils.showInfoNotification(project, String.valueOf(cnt) + " injections added to " + file.getName());
			}
		} else { // just notify user about no element selected
			Utils.showInfoNotification(project, "No injection was selected");
		}
	}
}
