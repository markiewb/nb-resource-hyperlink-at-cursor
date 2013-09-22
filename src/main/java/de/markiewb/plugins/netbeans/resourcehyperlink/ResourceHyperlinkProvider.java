package de.markiewb.plugins.netbeans.resourcehyperlink;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProvider;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;

/**
 * Code based on https://github.com/hantsy/click4nb/blob/master/click.support/src/org/netbeans/modules/web/click/editor/hyperlinks/ClickHyperlinkProvider.java
 *
 * @author markiewb
 */
@MimeRegistration(mimeType = "text/x-java", service = HyperlinkProvider.class)
public class ResourceHyperlinkProvider implements HyperlinkProvider {

    private int startOffset;
    private int endOffset;

    private FileObject fileToOpen;
    private String linkTarget;

    @Override
    public boolean isHyperlinkPoint(Document document, int offset) {
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

                linkTarget = resourceToken.text().subSequence(1, resourceToken.length() - 1).toString();
                return true;
            }

        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        return false;
    }

    private FileObject findResourceFileObject(Document doc, String path) {
        FileObject docFO = NbEditorUtilities.getFileObject(doc);
        FileObject currentDir = docFO.getParent();
        return currentDir.getFileObject(path);
    }

    @Override
    public int[] getHyperlinkSpan(Document doc, int position) {
        return new int[]{startOffset, endOffset};
    }

    @Override
    public void performClickAction(Document doc, int position) {
        fileToOpen = findResourceFileObject(doc, linkTarget);
        if (fileToOpen == null) {
            StatusDisplayer.getDefault().setStatusText("Invalid path :" + linkTarget);
            return;
        }
        openInEditor(fileToOpen);
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

//        String lowerCaseResource = resource.substring(1, resource.length()-1).toLowerCase();
//        if (lowerCaseResource.endsWith(".jsp")
//                || lowerCaseResource.endsWith(".htm")
//                || lowerCaseResource.endsWith(".css")
//                || lowerCaseResource.endsWith(".js")) {
//            return true;
//        }
//        return false;
        return false;
    }
}
