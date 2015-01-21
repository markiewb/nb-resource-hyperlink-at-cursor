/*
 * Copyright 2015 markiewb.
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

import static de.markiewb.netbeans.plugins.resourcehyperlink.ResourceHyperlinkProvider.cache;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import javax.swing.text.Document;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;

/**
 *
 * @author markiewb
 */
public class CacheableHyperlinkProvider<T> implements HyperlinkProviderExt {

    HyperlinkProviderExt delegate;
    FillCacheFunction<T> fillCacheFunction;
    Cache<T> cache = new Cache<T>();
    
    public CacheableHyperlinkProvider(HyperlinkProviderExt delegate, FillCacheFunction<T> fillCacheFunction) {
        this.delegate = delegate;
        this.fillCacheFunction = fillCacheFunction;
    }

    static class Cache<T> {

        int request_offset;
        Date request_lastUpdated;
        int startOffset;
        int endOffset;
        String request_filePath;
        T matches;
    }
    
    interface FillCacheFunction<T>{
        T fillCache(Document doc, int offset);
    }

    
    
    @Override
    public Set<HyperlinkType> getSupportedHyperlinkTypes() {
        return delegate.getSupportedHyperlinkTypes();
    }

    @Override
    public boolean isHyperlinkPoint(Document doc, int offset, HyperlinkType type) {
        updateCacheIfNecessary(doc, offset);
        return delegate.isHyperlinkPoint(doc, offset, type);
    }

    @Override
    public int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
        updateCacheIfNecessary(doc, offset);
        return delegate.getHyperlinkSpan(doc, offset, type);
    }

    @Override
    public void performClickAction(Document doc, int offset, HyperlinkType type) {
        delegate.performClickAction(doc, offset, type);
    }

    @Override
    public String getTooltipText(Document doc, int offset, HyperlinkType type) {
        updateCacheIfNecessary(doc, offset);
        return delegate.getTooltipText(doc, offset, type);
    }
    private static final int EXPIRE_CACHE_IN_SECONDS = 2;

    private void updateCacheIfNecessary(Document doc, int offset) {
        boolean isSameRequest = false;
        boolean timeExpired = false;
        FileObject fileObject = NbEditorUtilities.getFileObject(doc);
        if (null != cache.request_filePath && null != cache.request_lastUpdated) {
            final boolean sameFile = fileObject.getPath().equals(cache.request_filePath);
            //if there was a previously match
            boolean withinOffSetRange = cache.startOffset <= offset && offset <= cache.endOffset;
            //if there was not a previously match            
            final boolean sameOffset = offset == cache.request_offset;

            Calendar nowMinus2Seconds = java.util.Calendar.getInstance();
            nowMinus2Seconds.roll(Calendar.SECOND, -1 * EXPIRE_CACHE_IN_SECONDS);
            timeExpired = cache.request_lastUpdated.before(nowMinus2Seconds.getTime());

            isSameRequest = sameFile && (withinOffSetRange || sameOffset);

        }
        if (isSameRequest && !timeExpired) {

        } else {
            cache.request_filePath = fileObject.getPath();
            cache.request_offset = offset;

            cache.request_lastUpdated = new Date();
            cache.startOffset=-1;
            cache.endOffset=-1;
            cache.matches = fillCacheFunction.fillCache(doc, offset);
            System.out.println(String.format("cacheMiss = %s  %s", offset, fileObject));

        }

    }

}
