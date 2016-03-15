package com.tqmall.search.commons.match;

import com.tqmall.search.commons.nlp.NlpUtils;
import com.tqmall.search.commons.trie.Node;

import java.util.*;

/**
 * Created by xing on 16/2/20.
 * 字符串匹配器, 将算法抽取出来, 与数据隔离
 */
public abstract class TextMatcher<V> implements TextMatch<V> {

    protected final Node<V> root;

    protected TextMatcher(Node<V> root) {
        this.root = root;
    }

    /**
     * 具体匹配实现
     *
     * @param text 待匹配的字符数组
     * @return 匹配结果, 如果返回的list认为错误,整个文本处理结果返回null
     */
    protected abstract List<Hit<V>> runMatch(final char[] text, final int startPos, final int endPos);

    /**
     * 文本匹配
     */
    @Override
    public final List<Hit<V>> match(char[] text) {
        Objects.requireNonNull(text);
        return match(text, 0, text.length);
    }

    @Override
    public final List<Hit<V>> match(char[] text, int startPos, int length) {
        final int endPos = startPos + length;
        NlpUtils.arrayIndexCheck(text, startPos, endPos);
        if (length == 0) return null;
        return runMatch(text, startPos, endPos);
    }

    public static <V> TextMatcher<V> minMatcher(Node<V> root, boolean reverse) {
        return reverse ? new MinReverseTextMatcher<>(root) : new MinTextMatcher<>(root);
    }

    public static <V> TextMatcher<V> maxMatcher(Node<V> root, boolean reverse) {
        return reverse ? new MaxReverseTextMatcher<>(root) : new MaxTextMatcher<>(root);
    }

    /**
     * 正向最小匹配
     * 通过逆向前缀树也可以实现逆向匹配, 而且相率更高
     */
    public static class MinTextMatcher<V> extends TextMatcher<V> {

        public MinTextMatcher(Node<V> root) {
            super(root);
        }

        @Override
        protected List<Hit<V>> runMatch(final char[] text, final int startPos, final int endPos) {
            List<Hit<V>> hits = new LinkedList<>();
            Node<V> currentNode = root;
            int matchStartPos = -1;
            int i = startPos;
            while (i < endPos) {
                Node<V> nextNode = currentNode.getChild(text[i]);
                if (nextNode == null || nextNode.getStatus() == Node.Status.DELETE) {
                    if (currentNode != root) {
                        //没有对应匹配的词, 跳过, 从记录的matchStartPos开始下一个
                        i = matchStartPos;
                        matchStartPos = -1;
                        currentNode = root;
                    }
                    i++;
                } else {
                    if (matchStartPos == -1) matchStartPos = i;
                    i++;
                    if (nextNode.accept()) {
                        //匹配到一个词了~~~
                        hits.add(new Hit<>(text, matchStartPos, i, nextNode.getValue()));
                        currentNode = root;
                        matchStartPos = -1;
                    } else {
                        currentNode = nextNode;
                    }
                }
            }
            return hits;
        }
    }

    /**
     * 逆向最小匹配, 这个效率不高, 推荐逆向前缀树正向匹配实现之,效率更高
     */
    public static class MinReverseTextMatcher<V> extends TextMatcher<V> {

        public MinReverseTextMatcher(Node<V> root) {
            super(root);
        }

        @Override
        protected List<Hit<V>> runMatch(final char[] text, final int startPos, final int endPos) {
            List<Hit<V>> hits = new LinkedList<>();
            Node<V> currentNode = root;
            int i = endPos - 1, matchStartPos = endPos, lastPos = endPos;
            while (i >= startPos) {
                Node<V> nextNode = i < lastPos ? currentNode.getChild(text[i]) : null;
                if (nextNode == null || nextNode.getStatus() == Node.Status.DELETE) {
                    //没有匹配到, 向前移
                    if (currentNode == root) {
                        i--;
                    } else {
                        i = matchStartPos - 1;
                        currentNode = root;
                    }
                } else {
                    //startPos只会不断减小
                    if (i < matchStartPos) matchStartPos = i;
                    i++;
                    if (nextNode.accept()) {
                        //匹配到一个词了~~~
                        hits.add(new Hit<>(text, matchStartPos, i, nextNode.getValue()));
                        i = matchStartPos - 1;
                        lastPos = matchStartPos;
                        currentNode = root;
                    } else {
                        currentNode = nextNode;
                    }
                }
            }
            return hits;
        }
    }

    /**
     * 正向最大匹配
     * 通过逆向前缀树也可以实现逆向匹配, 而且相率更高
     */
    public static class MaxTextMatcher<V> extends TextMatcher<V> {

        public MaxTextMatcher(Node<V> root) {
            super(root);
        }

        @Override
        protected List<Hit<V>> runMatch(final char[] text, final int startPos, final int endPos) {
            List<Hit<V>> hits = new LinkedList<>();
            Node<V> currentNode = root;
            int matchStartPos = -1, matchEndPos = -1, i = startPos;
            V lastMatchValue = null;
            while (i < endPos) {
                Node<V> nextNode = currentNode.getChild(text[i]);
                if (nextNode == null || nextNode.getStatus() == Node.Status.DELETE) {
                    if (matchEndPos != -1) {
                        //匹配到一个最大词~~~
                        hits.add(new Hit<>(text, matchStartPos, matchEndPos, lastMatchValue));
                        i = matchEndPos;
                        matchEndPos = -1;
                    } else if (root == currentNode) {
                        i++;
                    } else {
                        currentNode = root;
                    }
                    matchStartPos = -1;
                } else {
                    if (matchStartPos == -1) matchStartPos = i;
                    i++;
                    if (nextNode.accept()) {
                        matchEndPos = i;
                        lastMatchValue = nextNode.getValue();
                    }
                    currentNode = nextNode;
                }
            }
            if (matchEndPos != -1) {
                //捡个漏
                hits.add(new Hit<>(text, matchStartPos, matchEndPos, lastMatchValue));
            }
            return hits;
        }
    }

    /**
     * 逆向最大匹配, 这个效率不高, 推荐逆向前缀树正向匹配实现之,效率更高
     */
    public static class MaxReverseTextMatcher<V> extends TextMatcher<V> {

        public MaxReverseTextMatcher(Node<V> root) {
            super(root);
        }

        /**
         * 新加入的词有重叠的小词, 删除
         */
        private void appendHit(Hit<V> hit, LinkedList<Hit<V>> hits) {
            int hitEndPos = hit.getEndPos();
            Iterator<Hit<V>> it = hits.descendingIterator();
            int removeCount = 0;
            while (it.hasNext()) {
                Hit<V> h = it.next();
                if (hitEndPos <= h.getStartPos()) break;
                else if (hitEndPos < h.getEndPos()) return;
                else {
                    removeCount++;
                    if (hitEndPos == h.getEndPos()) break;
                }
            }
            if (removeCount > 0) {
                while (removeCount-- > 0) hits.pollLast();
            }
            hits.add(hit);
        }

        @Override
        protected List<Hit<V>> runMatch(final char[] text, final int startPos, final int endPos) {
            Node<V> currentNode = root;
            int i = endPos - 1, hitStartPos = endPos, hitEndPos = endPos;
            boolean lastAccept = false;
            V hitValue = null;
            LinkedList<Hit<V>> hits = new LinkedList<>();
            while (i >= startPos) {
                Node<V> nextNode = i < text.length ? currentNode.getChild(text[i]) : null;
                if (nextNode == null || nextNode.getStatus() == Node.Status.DELETE) {
                    //没有匹配到, 向前移
                    if (currentNode == root) {
                        i--;
                    } else {
                        if (lastAccept) {
                            //匹配到一个词了~~~
                            appendHit(new Hit<>(text, hitStartPos, hitEndPos, hitValue), hits);
                            lastAccept = false;
                        }
                        i = hitStartPos - 1;
                        currentNode = root;
                    }
                } else {
                    //startPos只会不断减小
                    if (i < hitStartPos) hitStartPos = i;
                    i++;
                    if (nextNode.accept()) {
                        lastAccept = true;
                        hitEndPos = i;
                        hitValue = nextNode.getValue();
                    }
                    currentNode = nextNode;
                }
            }
            if (lastAccept) {
                appendHit(new Hit<>(text, hitStartPos, hitEndPos, hitValue), hits);
            }
            return hits;
        }

    }

}
