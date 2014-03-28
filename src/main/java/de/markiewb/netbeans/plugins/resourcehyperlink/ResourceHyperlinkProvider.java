/*
 * Copyright 2013 markiewb.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.markiewb.netbeans.plugins.resourcehyperlink;

import de.markiewb.netbeans.plugins.resourcehyperlink.options.ConfigPanel;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
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
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.NbPreferences;

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

    boolean enablePartialMatches;

    public ResourceHyperlinkProvider() {
        Preferences pref = NbPreferences.forModule(ConfigPanel.class);
        enablePartialMatches = pref.getBoolean(ConfigPanel.PARTIAL_MATCHING, ConfigPanel.PARTIAL_MATCHING_DEFAULT);
        pref.addPreferenceChangeListener(new PreferenceChangeListener() {

            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                if (evt.getKey().equals(ConfigPanel.PARTIAL_MATCHING)) {
                    enablePartialMatches = evt.getNode().getBoolean(ConfigPanel.PARTIAL_MATCHING, ConfigPanel.PARTIAL_MATCHING_DEFAULT);
                }
            }
        });
    }
    

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
                if (findFiles.isEmpty()) {
                    return ResultTO.createEmpty();
                }
                return ResultTO.create(startOffset, endOffset, linkTarget, findFiles);
            }

        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        return ResultTO.createEmpty();
    }

    private Set<FileObject> findFiles(Document doc, String path) {
        Set<FileObject> result = new HashSet<FileObject>();

        //a) exists in current dir? exact matching
        final FileObject fileInCurrentDirectory = getMatchingFileInCurrentDirectory(doc, path);
        if (null != fileInCurrentDirectory) {
            result.add(fileInCurrentDirectory);
        }
        
        //b) exists in current dir? partial matching
        Collection<FileObject> partialMatches = partialMatches(path, NbEditorUtilities.getFileObject(doc).getParent().getChildren());
        if (null != partialMatches) {
            result.addAll(partialMatches);
        }

        //c) fallback to search in all source roots
        FileObject docFO = NbEditorUtilities.getFileObject(doc);
        result.addAll(getMatchingFilesFromSourceRoots(FileOwnerQuery.getOwner(docFO), path));

        //d) fallback to support absolute paths - exact match
        if (new File(path).exists() && !FileUtil.toFileObject(FileUtil.normalizeFile(new File(path))).isFolder()) {
            FileObject absolutePath = FileUtil.toFileObject(FileUtil.normalizeFile(new File(path)));
            result.add(absolutePath);
        }

        return result;
    }

    private List<FileObject> getMatchingFilesFromSourceRoots(Project p, String searchToken) {
        List<SourceGroup> list = new ArrayList<SourceGroup>();
        List<FileObject> foundMatches = new ArrayList<FileObject>();
        final Sources sources = ProjectUtils.getSources(p);
        list.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)));
        list.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_RESOURCES)));
        list.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_HINT_TEST)));
        for (SourceGroup sourceGroup : list) {

            //partial matches
            Collection<FileObject> partialMatches = partialMatches(searchToken, sourceGroup.getRootFolder().getChildren());
            foundMatches.addAll(partialMatches);

            //exact matches
            FileObject fileObject = sourceGroup.getRootFolder().getFileObject(searchToken);
            if (fileObject != null && !fileObject.isFolder()) {
                foundMatches.add(fileObject);
            }
        }
        return foundMatches;
    }

    private Collection<FileObject> partialMatches(final String searchToken, FileObject[] candidates) {
        List<FileObject> result = new ArrayList<FileObject>();
        final String lowerCaseToken = searchToken.toLowerCase();
        for (FileObject fileObject : candidates) {
            if (fileObject.isFolder()) {
                continue;
            }

            if (enablePartialMatches) {
                //partial matches
                //f.e. "def" matches "abcdefg.txt" and "defcon.png"
                boolean containsPartialMatches = fileObject.getNameExt().toLowerCase().contains(lowerCaseToken);
                if (containsPartialMatches) {
                    result.add(fileObject);
                }

            } else {
                //exact matching
                boolean containsPartialMatches = fileObject.getNameExt().toLowerCase().equals(lowerCaseToken);
                if (containsPartialMatches) {
                    result.add(fileObject);
                }
            }
        }
        return result;
    }

    private FileObject getMatchingFileInCurrentDirectory(Document doc, String path) {
        FileObject docFO = NbEditorUtilities.getFileObject(doc);
        FileObject currentDir = docFO.getParent();
        final FileObject fileObject = currentDir.getFileObject(path);
        if (null != fileObject && !fileObject.isFolder()) {
            return fileObject;
        } else {
            return null;
        }
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
            List<FileObjectTuple> collector = new ArrayList<FileObjectTuple>();

            for (FileObject fileObject : indexedFilePaths) {
                //convert absolute path to relative regarding the project
                String path1 = fileObject.getPath().substring(project.getProjectDirectory().getPath().length());
                collector.add(new FileObjectTuple(fileObject, path1));
            }
            Collections.sort(collector, new Comparator<FileObjectTuple>() {
                @Override
                public int compare(FileObjectTuple o1, FileObjectTuple o2) {
                    return o1.getSecond().compareToIgnoreCase(o2.getSecond());
                }
            });

            DefaultComboBoxModel<FileObjectTuple> defaultComboBoxModel = new DefaultComboBoxModel<FileObjectTuple>();
            for (FileObjectTuple item : collector) {
                defaultComboBoxModel.addElement(item);
            }
            
            //TODO replace with floating listbox like "Open implementations"
            final JComboBox<FileObjectTuple> jList = new JComboBox<FileObjectTuple>(defaultComboBoxModel);
            if (DialogDisplayer.getDefault().notify(new DialogDescriptor(jList, "Multiple files found. Please choose:")) == NotifyDescriptor.OK_OPTION) {
                return defaultComboBoxModel.getElementAt(jList.getSelectedIndex()).getFirst();
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

    private static class FileObjectTuple extends Pair<FileObject, String> {

        public FileObjectTuple(FileObject first, String second) {
            super(first, second);
        }

        @Override
        public String toString() {
            return getSecond();
        }
    }
    
    private static class Pair<First, Second> {

        private final First first;
        private final Second second;

        private Pair(final First first, final Second second) {
            this.first = first;
            this.second = second;
        }

        public First getFirst() {
            return first;
        }

        public Second getSecond() {
            return second;
        }

    }

}
