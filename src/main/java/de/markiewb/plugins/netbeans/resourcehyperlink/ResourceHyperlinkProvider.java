package de.markiewb.plugins.netbeans.resourcehyperlink;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
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
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;

@MimeRegistration(mimeType = "text/x-java", service = HyperlinkProviderExt.class)
public class ResourceHyperlinkProvider implements HyperlinkProviderExt {

    private int startOffset;
    private int endOffset;

    private String linkTarget;

    @Override
    public boolean isHyperlinkPoint(Document document, int offset, HyperlinkType type) {
        if (!(document instanceof BaseDocument)) {
            return false;
        }

        BaseDocument doc = (BaseDocument) document;
        JTextComponent target = Utilities.getFocusedComponent();

        if ((target == null) || (target.getDocument() != doc)) {
            return false;
        }

        try {
            TokenHierarchy<String> hi = TokenHierarchy.create(doc.getText(0, doc.getLength()), JavaTokenId.language());
            TokenSequence<JavaTokenId> ts = hi.tokenSequence(JavaTokenId.language());

            ts.move(offset);
            boolean lastTokenInDocument = !ts.moveNext();
            if (lastTokenInDocument) {
                // end of the document
                return false;
            }
            while (ts.token() == null || ts.token().id() == JavaTokenId.WHITESPACE) {
                ts.movePrevious();
            }

            Token<JavaTokenId> resourceToken = ts.offsetToken();
            if (null == resourceToken
                    || resourceToken.length() <= 2) {
                return false;
            }

            if (containResource(resourceToken.text().toString())
                    && resourceToken.id() == JavaTokenId.STRING_LITERAL // identified must be string
                    && resourceToken.length() > 2) { // identifier must be longer than "" string
                startOffset = resourceToken.offset(hi) + 1;
                endOffset = resourceToken.offset(hi) + resourceToken.length() - 1;

                if (startOffset > endOffset) {
                    endOffset = startOffset;
                }
                final String wholeText = resourceToken.text().subSequence(1, resourceToken.length() - 1).toString();

                final int offSetAtCursor = (offset - startOffset) + 1;
                int indexOf = getIndexOfNextPathSeparator(wholeText, offSetAtCursor);
                if (indexOf < 0) {
                    indexOf = wholeText.length();
                }
                endOffset = startOffset + Math.min(indexOf + 1, wholeText.length());
                final String innerSelectedText = resourceToken.text().subSequence(1, 1 + Math.min(indexOf + 1, wholeText.length())).toString();
                linkTarget = innerSelectedText;

                StatusDisplayer.getDefault().setStatusText("Path :" + startOffset + "/" + endOffset + "/" + offset + "//" + (offset - startOffset) + "=" + innerSelectedText);
                return true;
            }

        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        return false;
    }

    private int getIndexOfNextPathSeparator(final String wholeText, final int offSetAtCursor) {
        int indexOf = wholeText.indexOf("/", offSetAtCursor - 1);
        if (indexOf < 0) {
            //TODO fallback use windows separator
//            indexOf = wholeText.indexOf("\\", offSetAtCursor-1);
        }
        return indexOf;
    }

     private List<FileObject> findFiles(Document doc, String path) {
                 final FileObject fileInCurrentDirectory = getMatchingFileInCurrentDirectory(doc, path);


        //fallback to search in all source roots
        FileObject docFO = NbEditorUtilities.getFileObject(doc);
        List<FileObject> foundMatches =new ArrayList<FileObject>();
                
        foundMatches.addAll(getMatchingFilesFromSourceRoots(FileOwnerQuery.getOwner(docFO), path));
        
        if (null != fileInCurrentDirectory) {
            foundMatches.add(fileInCurrentDirectory);
        }
        return foundMatches;
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
        return new int[]{startOffset, endOffset};
    }

    @Override
    public void performClickAction(Document doc, int position, HyperlinkType type) {
        List<FileObject> foundMatches = findFiles(doc, linkTarget);
        final Project project = FileOwnerQuery.getOwner(NbEditorUtilities.getFileObject(doc));        
        FileObject fileToOpen = getSingleMatchOrAskForUserChoice(foundMatches, project);        

        if (fileToOpen == null) {
            StatusDisplayer.getDefault().setStatusText("Invalid path :" + linkTarget);
            return;
        }
        openInEditor(fileToOpen);
    }

    private FileObject getSingleMatchOrAskForUserChoice(List<FileObject> foundMatches, Project project) {
        if (foundMatches.size() == 1) {
            return foundMatches.get(0);
        }
        if (foundMatches.size() >= 2) {
            List<String> list1 = new ArrayList<String>();

            for (FileObject fileObject : foundMatches) {
                //convert absolute path to relative regarding the project
                String path1 = fileObject.getPath().substring(project.getProjectDirectory().getPath().length());
                list1.add(path1);
            }
            String[] strings = list1.toArray(new String[list1.size()]);

            final JComboBox<String> jList = new JComboBox<String>(strings);
            if (DialogDisplayer.getDefault().notify(new DialogDescriptor(jList, "Multiple files found. Please choose:")) == NotifyDescriptor.OK_OPTION) {
                return foundMatches.get(jList.getSelectedIndex());
            } else {
                //
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

    private boolean containResource(String resource) {
        if (resource.length() < 2) {
            return false;
        }
        //TODO check if file exists

//        String lowerCaseResource = resource.substring(1, resource.length()-1).toLowerCase();
//        if (lowerCaseResource.endsWith(".jsp")
//                || lowerCaseResource.endsWith(".htm")
//                || lowerCaseResource.endsWith(".css")
//                || lowerCaseResource.endsWith(".js")) {
//            return true;
//        }
//        return false;
        return true;
    }

    @Override
    public Set<HyperlinkType> getSupportedHyperlinkTypes() {
        return EnumSet.of(HyperlinkType.GO_TO_DECLARATION);
    }

    @Override
    public String getTooltipText(Document doc, int offset, HyperlinkType type) {
        //no tooltip until https://netbeans.org/bugzilla/show_bug.cgi?id=236249 is resolved
        return null;

//        //call calculation first, because the order of method calls in undefined (by API doc)
//        boolean hyperlinkPoint = isHyperlinkPoint(doc, offset, type);
//         StatusDisplayer.getDefault().setStatusText(""+offset+"/"+type);
//        if (hyperlinkPoint) {
//            return "Open " + linkTarget;
//        } else {
//            return null;
//        }
    }

}
