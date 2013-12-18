package de.markiewb.plugins.netbeans.resourcehyperlink;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComboBox;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.cookies.EditCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;

/**
 * Hyperlink provider opening resources which are encoded in string literals
 * within java files.
 * <p>
 * For example: {@code "com/foo/Bar.java"} will be resolved in the source-roots
 * of
 * {@link JavaProjectConstants.SOURCES_TYPE_JAVA}, {@link JavaProjectConstants.SOURCES_TYPE_RESOURCES}, {@link JavaProjectConstants.SOURCES_HINT_TEST}
 * </p>
 * If there are multiple matches a dialog will pop up and let the user choose.
 *
 * @author markiewb
 */
@MimeRegistration(mimeType = "text/x-java", service = HyperlinkProviderExt.class)
public class ResourceHyperlinkProvider implements HyperlinkProviderExt {

    @Override
    public boolean isHyperlinkPoint(Document document, int offset, HyperlinkType type) {
        ResultTO matches = findResources(document, offset);
        return matches.isValid();
    }

    private ResultTO findResources(Document document, int offset) {
        if (!(document instanceof BaseDocument)) {
            return ResultTO.createEmpty();
        }

        BaseDocument doc = (BaseDocument) document;
        JTextComponent target = Utilities.getFocusedComponent();

        if ((target == null) || (target.getDocument() != doc)) {
            return ResultTO.createEmpty();
        }

        try {
            TokenHierarchy<String> hi = TokenHierarchy.create(doc.getText(0, doc.getLength()), JavaTokenId.language());
            TokenSequence<JavaTokenId> ts = hi.tokenSequence(JavaTokenId.language());

            ts.move(offset);
            boolean lastTokenInDocument = !ts.moveNext();
            if (lastTokenInDocument) {
                // end of the document
                return ResultTO.createEmpty();
            }
            while (ts.token() == null || ts.token().id() == JavaTokenId.WHITESPACE) {
                ts.movePrevious();
            }

            Token<JavaTokenId> resourceToken = ts.offsetToken();
            if (null == resourceToken
                    || resourceToken.length() <= 2) {
                return ResultTO.createEmpty();
            }

            if (resourceToken.id() == JavaTokenId.STRING_LITERAL // identified must be string
                    && resourceToken.length() > 2) { // identifier must be longer than "" string
                int startOffset = resourceToken.offset(hi) + 1;

                final String wholeText = resourceToken.text().subSequence(1, resourceToken.length() - 1).toString();

                int endOffset = startOffset + wholeText.length();
                String linkTarget = wholeText;

//                StatusDisplayer.getDefault().setStatusText("Path :" + startOffset + "/" + endOffset + "/" + offset + "//" + (offset - startOffset) + "=" + innerSelectedText);
                Set<FileObject> findFiles = findFiles(doc, linkTarget);
                return ResultTO.create(startOffset, endOffset, linkTarget, findFiles);
            }

        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        return ResultTO.createEmpty();
    }

    private Set<FileObject> findFiles(Document doc, String path) {
        final FileObject fileInCurrentDirectory = getMatchingFileInCurrentDirectory(doc, path);

        //fallback to search in all source roots
        FileObject docFO = NbEditorUtilities.getFileObject(doc);
        Set<FileObject> matches = new HashSet<FileObject>();

        matches.addAll(getMatchingFilesFromSourceRoots(FileOwnerQuery.getOwner(docFO), path));

        if (null != fileInCurrentDirectory) {
            matches.add(fileInCurrentDirectory);
        }
        return matches;
    }

    private List<FileObject> getMatchingFilesFromSourceRoots(Project p, String path) {
        List<SourceGroup> list = new ArrayList<SourceGroup>();
        List<FileObject> foundMatches = new ArrayList<FileObject>();
        final Sources sources = ProjectUtils.getSources(p);
        list.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)));
        list.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_RESOURCES)));
        list.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_HINT_TEST)));
        for (SourceGroup sourceGroup : list) {
            FileObject fileObject = sourceGroup.getRootFolder().getFileObject(path);
            if (fileObject != null) {
                foundMatches.add(fileObject);
            }
        }
        return foundMatches;
    }

    private FileObject getMatchingFileInCurrentDirectory(Document doc, String path) {
        FileObject docFO = NbEditorUtilities.getFileObject(doc);
        FileObject currentDir = docFO.getParent();
        return currentDir.getFileObject(path);
    }

    @Override
    public int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
        ResultTO matches = findResources(doc, offset);
        if (matches.isValid()) {
            return new int[]{matches.startOffsetInLiteral, matches.endOffsetInLiteral};
        } else {
            return new int[]{-1, -1};
        }
    }

    @Override
    public void performClickAction(Document doc, int position, HyperlinkType type) {
        ResultTO matches = findResources(doc, position);
        if (matches.isValid()) {
            Collection<FileObject> foundMatches = matches.foundFiles;
            final Project project = FileOwnerQuery.getOwner(NbEditorUtilities.getFileObject(doc));
            FileObject fileToOpen = getSingleMatchOrAskForUserChoice(foundMatches, project);

            if (fileToOpen == null) {
//                StatusDisplayer.getDefault().setStatusText("Invalid path: " + findMatches.linkTarget);
                return;
            }
            openInEditor(fileToOpen);
        }
    }

    private FileObject getSingleMatchOrAskForUserChoice(Collection<FileObject> foundMatches, Project project) {
        if (foundMatches.size() == 1) {
            return foundMatches.iterator().next();
        }
        List<FileObject> indexedFilePaths = new ArrayList<FileObject>(foundMatches);

        if (foundMatches.size() >= 2) {
            List<String> collector = new ArrayList<String>();

            for (FileObject fileObject : indexedFilePaths) {
                //convert absolute path to relative regarding the project
                String path1 = fileObject.getPath().substring(project.getProjectDirectory().getPath().length());
                collector.add(path1);
            }
            Collections.sort(collector);

            //TODO replace with floating listbox like "Open implementations"
            final JComboBox<String> jList = new JComboBox<String>(collector.toArray(new String[collector.size()]));
            if (DialogDisplayer.getDefault().notify(new DialogDescriptor(jList, "Multiple files found. Please choose:")) == NotifyDescriptor.OK_OPTION) {
                return indexedFilePaths.get(jList.getSelectedIndex());
            }
        }
        return null;
    }

    public static void openInEditor(FileObject fileToOpen) {
        DataObject fileDO;
        try {
            fileDO = DataObject.find(fileToOpen);
            if (fileDO != null) {
                EditCookie editCookie = fileDO.getLookup().lookup(EditCookie.class);
                if (editCookie != null) {
                    editCookie.edit();
                } else {
                    OpenCookie openCookie = fileDO.getLookup().lookup(OpenCookie.class);
                    if (openCookie != null) {
                        openCookie.open();
                    }
                }
            }
        } catch (DataObjectNotFoundException e) {
            Exceptions.printStackTrace(e);
        }

    }

    @Override
    public Set<HyperlinkType> getSupportedHyperlinkTypes() {
        return EnumSet.of(HyperlinkType.GO_TO_DECLARATION);
    }

    @Override
    public String getTooltipText(Document doc, int offset, HyperlinkType type) {
        ResultTO result = findResources(doc, offset);
        if (!result.isValid()) {
            return null;
        }

        Collection<FileObject> findMatches = result.foundFiles;
        if (findMatches.size() < 0) {
            return null;
        }
        return MessageFormat.format("<html>Open <b>{0}</b>{1,choice,0#|1#|1< ({1} matches)}", result.linkTarget, findMatches.size());
    }

    private static class ResultTO {

        int startOffsetInLiteral;
        int endOffsetInLiteral;

        String linkTarget;

        ResultTO(int startOffset, int endOffset, String linkTarget, Collection<FileObject> foundFiles) {
            this.startOffsetInLiteral = startOffset;
            this.endOffsetInLiteral = endOffset;
            this.linkTarget = linkTarget;
            this.foundFiles = foundFiles;
        }
        Collection<FileObject> foundFiles;

        boolean isValid() {
            return !foundFiles.isEmpty();
        }

        static ResultTO createEmpty() {
            return new ResultTO(-1, -1, null, Collections.<FileObject>emptySet());
        }

        static ResultTO create(int startOffset, int endOffset, String linkTarget, Collection<FileObject> foundFiles) {
            return new ResultTO(startOffset, endOffset, linkTarget, foundFiles);
        }

    }

}
