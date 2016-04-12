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
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.lang.model.element.TypeElement;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ElementHandle;
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
 * and in the Maven-Source-Roots
 * </p>
 * It also resolves FQN classnames. (since 1.3.0) It also resolved files in same
 * package but different source root. If there are multiple matches a dialog
 * will pop up and let the user choose.
 *
 * @author markiewb
 */
@MimeRegistration(mimeType = "text/x-java", service = HyperlinkProviderExt.class)
public class ResourceHyperlinkProvider implements HyperlinkProviderExt {

    /**
     * Copied from org.netbeans.modules.maven.classpath.MavenSourcesImpl. These
     * constants where not public API, so they are duplicated in here.
     * https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/9
     */
    public static final String MAVEN_TYPE_OTHER = "Resources"; //NOI18N
    public static final String MAVEN_TYPE_TEST_OTHER = "TestResources"; //NOI18N
    public static final String MAVEN_TYPE_GEN_SOURCES = "GeneratedSources"; //NOI18N
    public static final Cache<ResultTO> cache = new Cache<ResultTO>();
    private static final int EXPIRE_CACHE_IN_SECONDS = 10;
    private static final Logger LOG = Logger.getLogger(ResourceHyperlinkProvider.class.getName());
    
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
        updateCacheIfNecessary(document, offset);
        if (null == cache.matches) {
            return false;
        }
        return cache.matches.isValid();
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
                boolean movePrevious = ts.movePrevious();
                if (!movePrevious) {
                    /**
                     * Doc from {@link ​TokenSequence#movePrevious}: false if it
                     * was not moved because there are no more tokens in the
                     * backward direction.
                     */
                    break;
                }
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
                    return ResultTO.createEmpty(startOffset, endOffset);
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

        final FileObject docFO = NbEditorUtilities.getFileObject(doc);
        Project project = null;
        if (null != docFO) {
            project = FileOwnerQuery.getOwner(docFO);
        }

        //a) exists in current dir? exact matching
        final FileObject fileInCurrentDirectory = getMatchingFileInCurrentDirectory(doc, path);
        if (null != fileInCurrentDirectory) {
            result.add(fileInCurrentDirectory);
        }

        //b) exists in current dir? partial matching
        if (null != docFO && null != docFO.getParent()) {
            Collection<FileObject> partialMatches = partialMatches(path, docFO.getParent().getChildren());
            if (null != partialMatches) {
                result.addAll(partialMatches);
            }
        }

        //c) fallback to search partial and exact in all source roots
        if (null != project) {
            result.addAll(getMatchingFilesFromSourceRoots(project, path));
        }

        //d) fallback to exact matches in project root
        if (null != project) {
            FileObject projectDirectory = project.getProjectDirectory();
            if (null != projectDirectory) {
                //exact matches
                FileObject fileObjectAtProjectRoot = projectDirectory.getFileObject(path);
                if (fileObjectAtProjectRoot != null && !fileObjectAtProjectRoot.isFolder()) {
                    result.add(fileObjectAtProjectRoot);
                }
            }
        }

        //e) fallback to support absolute paths - exact match
        if (new File(path).exists() && !FileUtil.toFileObject(FileUtil.normalizeFile(new File(path))).isFolder()) {
            FileObject absolutePath = FileUtil.toFileObject(FileUtil.normalizeFile(new File(path)));
            result.add(absolutePath);
        }
        //f) support fqn classnames
        result.addAll(findByClassName(docFO, path));

        //g) fallback to partial matches of file in same package, but different sourceroot
        if (null != project && null != docFO) {
            result.addAll(getMatchingFilesFromOtherSourceRootsButWithinSamePackage(project, path, docFO));
        }
        return result;
    }

    private List<FileObject> getMatchingFilesFromSourceRoots(Project p, String searchToken) {
        List<FileObject> foundMatches = new ArrayList<FileObject>();
        for (SourceGroup sourceGroup : getAllSourceGroups(p)) {

            //partial matches
            Collection<FileObject> partialMatches = partialMatches(searchToken, sourceGroup.getRootFolder().getChildren());
            foundMatches.addAll(partialMatches);

            //exact matches, relative path
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
        final FileObject docFO = NbEditorUtilities.getFileObject(doc);
        if (null == docFO) {
            return null;
        }
        final FileObject currentDir = docFO.getParent();
        if (null == currentDir) {
            return null;
        }
        final FileObject fileObject = currentDir.getFileObject(path);
        if (null != fileObject && !fileObject.isFolder()) {
            return fileObject;
        } else {
            return null;
        }
    }

    @Override
    public int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
        updateCacheIfNecessary(doc, offset);
        ResultTO matches = cache.matches;
        if (matches.isValid()) {
            return new int[]{matches.startOffsetInLiteral, matches.endOffsetInLiteral};
        } else {
            return new int[]{-1, -1};
        }
    }

    private void updateCacheIfNecessary(Document doc, int offset) {
        boolean isSameRequest = false;
        boolean timeExpired = false;
        FileObject fileObject = NbEditorUtilities.getFileObject(doc);
        if (null != cache.request_filePath && null != cache.request_lastUpdated) {
            final boolean sameFile = getPathOrDefault(fileObject).equals(cache.request_filePath);
            //if there was a previously match
            boolean withinOffSetRange = cache.matches.startOffsetInLiteral <= offset && offset <= cache.matches.endOffsetInLiteral;
            //if there was not a previously match            
            final boolean sameOffset = offset == cache.request_offset;

            Calendar nowMinus2Seconds = java.util.Calendar.getInstance();
            nowMinus2Seconds.roll(Calendar.SECOND, -1 * EXPIRE_CACHE_IN_SECONDS);
            timeExpired = cache.request_lastUpdated.before(nowMinus2Seconds.getTime());

            isSameRequest = sameFile && (withinOffSetRange || sameOffset);

        }
        if (isSameRequest && !timeExpired) {

        } else {
            cache.request_filePath = getPathOrDefault(fileObject);
            cache.request_offset = offset;

            cache.request_lastUpdated = new Date();
            ResultTO matches = findResources(doc, offset);
            cache.matches = matches;
            LOG.fine(String.format("cacheMiss = %s  %s", offset, fileObject));
        }

    }

    @Override
    public void performClickAction(Document doc, int position, HyperlinkType type) {
        updateCacheIfNecessary(doc, position);
        if (null == cache.matches) {
            return;
        }
        
        ResultTO matches = cache.matches;
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
                String path1 = getPathOrDefault(fileObject).substring(getPathOrDefault(project.getProjectDirectory()).length());
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

    @Override
    public Set<HyperlinkType> getSupportedHyperlinkTypes() {
        return EnumSet.of(HyperlinkType.GO_TO_DECLARATION);
    }

    @Override
    public String getTooltipText(Document doc, int offset, HyperlinkType type) {
        updateCacheIfNecessary(doc, offset);

        ResultTO result = cache.matches;
        if (!result.isValid()) {
            return null;
        }

        Collection<FileObject> findMatches = result.foundFiles;
        if (findMatches.size() < 0) {
            return null;
        }
        return MessageFormat.format("<html>Open <b>{0}</b>{1,choice,0#|1#|1< ({1} matches)}", result.linkTarget, findMatches.size());
    }

    private Collection<FileObject> findByClassName(FileObject fo, String fqnClassName) {

        Set<FileObject> files = new java.util.LinkedHashSet<FileObject>();

        ClassPath bootCp = ClassPath.getClassPath(fo, ClassPath.BOOT);
        ClassPath compileCp = ClassPath.getClassPath(fo, ClassPath.COMPILE);
        ClassPath sourcePath = ClassPath.getClassPath(fo, ClassPath.SOURCE);
        if (null == bootCp || null == compileCp || null == sourcePath) {
            return files;
        }
        final ClasspathInfo info = ClasspathInfo.create(bootCp, compileCp, sourcePath);
        int lastIndexOfDot = fqnClassName.lastIndexOf(".");
        String simpleClassName;
        if (lastIndexOfDot > 0) {
            simpleClassName = fqnClassName.substring(lastIndexOfDot + 1);
        } else {
            simpleClassName = fqnClassName;
        }

        /**
         * Search in own project sources AND in sources of dependencies
         */
        final Set<ElementHandle<TypeElement>> result = info.getClassIndex().getDeclaredTypes(simpleClassName, ClassIndex.NameKind.SIMPLE_NAME, EnumSet.of(ClassIndex.SearchScope.SOURCE, ClassIndex.SearchScope.DEPENDENCIES));
        for (ElementHandle<TypeElement> te : result) {
            final String qualifiedName = te.getQualifiedName();
            if (!qualifiedName.equals(fqnClassName)) {
                continue;
            }

            //NOTE: will not return a file for a class without sources (f.e. maven dep)
            final FileObject file = org.netbeans.api.java.source.SourceUtils.getFile(te, info);
//            System.out.println(String.format("file = %s from %s", file, te));
            if (null != file) {
                files.add(file);
            }
        }
//                System.out.println("files = "+files.size() + files);
        return files;
    }

    /**
     * <pre>
     * Given
     *      String foo="MyTest-context.xml"
     * in
     *      src/test/java/com/foo/MyTest.java (src/test/java = sourceRoot A)
     * also matches
     *      src/test/resources/com/foo/MyTest-context.xml (src/test/resources = sourceRoot B)
     * </pre>
     *
     * @param p
     * @param searchToken
     * @param originFileObject
     * @see
     * https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/10
     * @return
     */
    private Collection<? extends FileObject> getMatchingFilesFromOtherSourceRootsButWithinSamePackage(Project p, String searchToken, FileObject originFileObject) {

        List<FileObject> foundMatches = new ArrayList<FileObject>();
        FileObject originFolder = originFileObject.getParent();
        if (null == originFolder) {
            return foundMatches;
        }

        String packageName = null;
        for (SourceGroup sourceGroup : getAllSourceGroups(p)) {
            //SourceGroup: c:/myprojects/project/src/main/java/
            //OriginFolder: c:/myprojects/project/src/main/java/com/foo/impl
            //Result: com/foo/impl (!=null so we found the source root)
            final FileObject rootFolder = sourceGroup.getRootFolder();
            if (null == rootFolder) {
                continue;
            }
            String relative = FileUtil.getRelativePath(rootFolder, originFolder);
            if (null != relative) {
                packageName = relative;
                break;
            }
        }

        if (null != packageName) {
            for (SourceGroup sourceGroup : getAllSourceGroups(p)) {
                final FileObject rootFolder = sourceGroup.getRootFolder();
                if (null == rootFolder) {
                    continue;
                }
                //-> c:/myprojects/project/src/test/java/com/foo
                final FileObject packageInSourceRoot = rootFolder.getFileObject(packageName);
                if (null == packageInSourceRoot) {
                    continue;
                }
                //exists c:/myprojects/project/src/test/java/com/foo/SEARCHTOKEN ?
                Collection<FileObject> partialMatches = partialMatches(searchToken, packageInSourceRoot.getChildren());
                foundMatches.addAll(partialMatches);
//            System.out.println(String.format("%s: found %s in new sourceroot %s", partialMatches, searchToken, packageInSourceRoot ));
            }
        }
        return foundMatches;
    }

    private List<SourceGroup> getAllSourceGroups(Project p) {
        final Sources sources = ProjectUtils.getSources(p);
        List<SourceGroup> list = new ArrayList<SourceGroup>();
        list.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)));
        list.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_RESOURCES)));
        list.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_HINT_TEST)));
        list.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_HINT_MAIN)));
        list.addAll(Arrays.asList(sources.getSourceGroups(MAVEN_TYPE_GEN_SOURCES)));
        list.addAll(Arrays.asList(sources.getSourceGroups(MAVEN_TYPE_OTHER)));
        list.addAll(Arrays.asList(sources.getSourceGroups(MAVEN_TYPE_TEST_OTHER)));
        return list;
    }

    private String getPathOrDefault(FileObject fo) {
        if (null == fo) {
            return "";
        }
        final String path = fo.getPath();
        if (null == path) {
            return "";
        }
        return path;
    }
    static class Cache<T> {

        int request_offset;
        Date request_lastUpdated;
        String request_filePath;
        T matches;
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
