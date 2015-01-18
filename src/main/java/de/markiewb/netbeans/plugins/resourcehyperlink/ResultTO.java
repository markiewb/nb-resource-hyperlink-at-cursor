/*
 * Copyright 2014 markiewb.
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

import java.util.Collection;
import java.util.Collections;
import org.openide.filesystems.FileObject;

/**
 *
 * @author markiewb
 */
class ResultTO {

    static ResultTO createEmpty(int startOffset, int endOffset) {
        return new ResultTO(startOffset, endOffset, null, Collections.<FileObject>emptySet());
    }
    static ResultTO createEmpty() {
        return new ResultTO(-1, -1, null, Collections.<FileObject>emptySet());
    }
    static ResultTO create(int startOffset, int endOffset, String linkTarget, Collection<FileObject> foundFiles) {
        return new ResultTO(startOffset, endOffset, linkTarget, foundFiles);
    }
    int startOffsetInLiteral;
    int endOffsetInLiteral;
    String linkTarget;

    Collection<FileObject> foundFiles;
    ResultTO(int startOffset, int endOffset, String linkTarget, Collection<FileObject> foundFiles) {
        this.startOffsetInLiteral = startOffset;
        this.endOffsetInLiteral = endOffset;
        this.linkTarget = linkTarget;
        this.foundFiles = foundFiles;
    }

    boolean isValid() {
        return !foundFiles.isEmpty();
    }

    
}
