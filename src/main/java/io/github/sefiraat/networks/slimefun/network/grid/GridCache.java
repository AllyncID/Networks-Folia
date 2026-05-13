package io.github.sefiraat.networks.slimefun.network.grid;

import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class GridCache {

    private int page;
    private int maxPages;
    @Nonnull
    private SortOrder sortOrder;
    @Nullable
    private String filter;
    @Nullable
    private List<Map.Entry<ItemStack, Integer>> cachedEntries;
    private volatile boolean dirty = true;

    public GridCache(int page, int maxPages, @Nonnull SortOrder sortOrder) {
        this.page = page;
        this.maxPages = maxPages;
        this.sortOrder = sortOrder;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    @Nonnull
    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(@Nonnull SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        this.setDirty(true);
    }

    @Nullable
    public String getFilter() {
        return filter;
    }

    public void setFilter(@Nullable String filter) {
        this.filter = filter;
        this.setDirty(true);
    }

    @Nullable
    public List<Map.Entry<ItemStack, Integer>> getCachedEntries() {
        return cachedEntries;
    }

    public void setCachedEntries(@Nullable List<Map.Entry<ItemStack, Integer>> cachedEntries) {
        this.cachedEntries = cachedEntries;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    enum SortOrder {
        ALPHABETICAL,
        NUMBER
    }
}