package com.tqmall.search.commons.nlp;

import com.tqmall.search.commons.nlp.trie.AcNormalNode;

/**
 * Created by xing on 16/1/28.
 * 匹配到的结果
 */
public class Hit<V> implements Comparable<Hit<V>> {
    /**
     * 模式串在母文本中的终止位置
     * startPos通过endPos和outputKey直接搞出来了
     * 符合左开右闭原则,即[startPos, endPos)
     */
    private final int endPos;

    private String matchKey;

    private final V value;

    /**
     * 进来匹配的字符
     *
     * @param endPos   匹配到的结束位置, 符合左开右闭原则,即[startPos, endPos)
     * @param matchKey 匹配到的输出文本
     * @param value    对应节点的value
     */
    public Hit(int endPos, String matchKey, V value) {
        this.endPos = endPos;
        this.matchKey = matchKey;
        this.value = value;
    }

    public int getStartPos() {
        return endPos - matchKey.length();
    }

    public int getEndPos() {
        return endPos;
    }

    public V getValue() {
        return value;
    }

    public String getMatchKey() {
        return matchKey;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(endPos).append(':').append(matchKey);
        if (value != null) {
            sb.append(':').append(value);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Hit)) return false;

        Hit<?> hit = (Hit<?>) o;

        if (endPos != hit.endPos) return false;
        return matchKey.equals(hit.matchKey);
    }

    @Override
    public int hashCode() {
        int result = endPos;
        result = 31 * result + matchKey.hashCode();
        return result;
    }

    @Override
    public int compareTo(Hit<V> o) {
        int cmp = Integer.compare(endPos, o.endPos);
        if (cmp == 0) {
            cmp = Integer.compare(getStartPos(), o.getStartPos());
        }
        return cmp;
    }

    /**
     * 将匹配到的node添加到hits中, 函数中对node.status, 即{@link AcNormalNode#accept()}不做判断
     *
     * @param endPos 匹配文本中的结束位置
     * @param node   匹配到的node, 其status不做判断
     * @param <V>    value泛型
     */
    public static <V> void appendHits(Hits<V> hits, final int endPos, final AcNormalNode<V> node) {
        hits.addHit(new Hit<>(endPos, node.getSingleOutput(), node.getValue()));
        if (node.getFailed() instanceof AcNormalNode) {
            AcNormalNode<V> failed = (AcNormalNode<V>) node.getFailed();
            if (failed.accept()) {
                hits.addHit(new Hit<>(endPos, failed.getSingleOutput(), failed.getValue()));
            }
        }
    }
}
