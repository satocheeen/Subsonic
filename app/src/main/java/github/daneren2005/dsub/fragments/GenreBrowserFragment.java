package github.daneren2005.dsub.fragments;

import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.support.v7.widget.AppCompatButton;
import android.widget.TextView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import github.daneren2005.dsub.R;
import github.daneren2005.dsub.adapter.SectionAdapter;
import github.daneren2005.dsub.domain.MusicDirectory.Entry;
import github.daneren2005.dsub.service.MusicService;
import github.daneren2005.dsub.service.MusicServiceFactory;
import github.daneren2005.dsub.util.Constants;
import github.daneren2005.dsub.util.LoadingTask;
import github.daneren2005.dsub.util.Util;
import github.daneren2005.dsub.util.TabBackgroundTask;
import github.daneren2005.dsub.view.AlbumView;
import github.daneren2005.dsub.view.GenreGroupView;
import github.daneren2005.dsub.view.UpdateView;

/** A genre stays the scope of all three browsing paths, including drill-downs. */
public class GenreBrowserFragment extends SubsonicFragment implements SectionAdapter.OnItemClickedListener<Serializable> {
    public static final String ARG_GENRE = "genre";
    private static final String ARG_GROUP = "genreGroup";
    private static final String ARG_GROUP_TAB = "genreGroupTab";
    private static final String STATE_TAB = "genreTab";
    private static final String STATE_ALBUMS = "genreAlbums";
    private static final int ALBUMS = 0, ARTISTS = 1, YEARS = 2;
    private static final int PAGE_SIZE = 500;

    private String genre;
    private int selectedTab;
    private ArrayList<Entry> albums;
    private RecyclerView recycler;
    private TabBackgroundTask<ArrayList<Entry>> loadTask;
    private LoadingTask<List<Entry>> shuffleTask;
    private Map<String, List<Entry>> groups = new LinkedHashMap<>();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        genre = getArguments().getString(ARG_GENRE);
        if (state != null) {
            selectedTab = state.getInt(STATE_TAB);
            albums = (ArrayList<Entry>) state.getSerializable(STATE_ALBUMS);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(STATE_TAB, selectedTab);
        state.putSerializable(STATE_ALBUMS, albums);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        View content = inflater.inflate(R.layout.abstract_recycler_fragment, layout, false);
        if (!getArguments().containsKey(ARG_GROUP)) {
            AppCompatButton shuffle = new AppCompatButton(context);
            shuffle.setText(R.string.genre_shuffle_all);
            shuffle.setOnClickListener(v -> shuffleGenre());
            layout.addView(shuffle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            TabLayout tabs = new TabLayout(context);
            tabs.setTabMode(TabLayout.MODE_FIXED);
            int[] titles = {R.string.genre_albums, R.string.genre_artists, R.string.genre_years};
            for (int title : titles) tabs.addTab(tabs.newTab().setText(title), false);
            tabs.getTabAt(selectedTab).select();
            tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab) {
                    selectedTab = tab.getPosition();
                    if (albums != null) showAlbums();
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
            layout.addView(tabs);
        }
        layout.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        rootView = layout;
        refreshLayout = (SwipeRefreshLayout) content;
        refreshLayout.setOnRefreshListener(this);
        recycler = (RecyclerView) content.findViewById(R.id.fragment_recycler);
        setupLayoutManager(recycler, false);
        setupScrollList(recycler);
        // This browser uses ordinary scrolling; the shared fast scroller has no section index here.
        content.findViewById(R.id.fragment_fast_scroller).setVisibility(View.GONE);
        String group = getArguments().getString(ARG_GROUP);
        setTitle(group == null ? genre : genre + " / " + group);
        if (albums == null) refresh(false); else showAlbums();
        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        onFinishSetupOptionsMenu(menu);
    }

    @Override
    protected void refresh(final boolean refresh) {
        if (recycler == null) return;
        if (loadTask != null) loadTask.cancel();
        loadTask = new TabBackgroundTask<ArrayList<Entry>>(this) {
            @Override public ArrayList<Entry> doInBackground() throws Exception {
                MusicService service = MusicServiceFactory.getMusicService(context);
                ArrayList<Entry> result = new ArrayList<>();
                java.util.HashSet<String> seen = new java.util.HashSet<>();
                for (int offset = 0; !isCancelled(); offset += PAGE_SIZE) {
                    List<Entry> page = service.getAlbumList("genres", genre, PAGE_SIZE, offset,
                            refresh, context, this).getChildren();
                    int added = 0;
                    for (Entry album : page) {
                        if (seen.add(album.getId())) { result.add(album); added++; }
                    }
                    // Also stop if an older server ignores offset and repeats its last page.
                    if (page.size() < PAGE_SIZE || added == 0) break;
                }
                return result;
            }
            @Override public void done(ArrayList<Entry> result) {
                albums = result;
                loadTask = null;
                showAlbums();
            }
            @Override public void error(Throwable error) {
                loadTask = null;
                // Preserve a previous successful result when a refresh fails.
                if (albums != null) showAlbums();
                else recycler.setVisibility(View.GONE);
                View progress = rootView.findViewById(R.id.tab_progress);
                progress.setVisibility(View.VISIBLE);
                progress.findViewById(R.id.tab_progress_spinner).setVisibility(View.GONE);
                ((TextView) progress.findViewById(R.id.tab_progress_message)).setText(R.string.genre_load_error);
                super.error(error);
            }
        };
        loadTask.execute();
    }

    private void shuffleGenre() {
        if (shuffleTask != null) return;
        shuffleTask = new LoadingTask<List<Entry>>(context) {
            @Override
            protected List<Entry> doInBackground() throws Exception {
                MusicService service = MusicServiceFactory.getMusicService(context);
                List<Entry> songs = new ArrayList<>();
                java.util.HashSet<String> seen = new java.util.HashSet<>();
                int offset = 0;
                while (!isCancelled()) {
                    List<Entry> page = service.getSongsByGenre(genre, PAGE_SIZE, offset, context, this).getChildren();
                    if (page.isEmpty()) break;
                    int added = 0;
                    for (Entry song : page) {
                        if (seen.add(song.getId())) {
                            added++;
                            if (!song.isDirectory() && !song.isVideo()) songs.add(song);
                        }
                    }
                    // Never silently play a partial list when a server ignores pagination.
                    if (added == 0) throw new java.io.IOException("The server repeated a genre song page.");
                    offset += page.size();
                    updateProgress(getString(R.string.genre_shuffle_loading, songs.size()));
                }
                return songs;
            }
            @Override
            protected void done(List<Entry> songs) {
                shuffleTask = null;
                if (songs.isEmpty()) {
                    Util.toast(context, R.string.common_empty);
                    return;
                }
                // Resolve all songs before replacing the queue, then use the existing shuffled playback path.
                download(songs, false, false, true, false, true);
            }
            @Override
            protected void error(Throwable error) {
                shuffleTask = null;
                super.error(error);
            }
            @Override
            public void cancel() {
                super.cancel();
                shuffleTask = null;
            }
        };
        shuffleTask.execute();
    }

    private String groupName(Entry album, int tab) {
        if (tab == ARTISTS) {
            String artist = album.getArtist();
            return artist == null || artist.trim().isEmpty() ? getString(R.string.genre_unknown_artist) : artist;
        }
        Integer year = album.getYear();
        return year == null || year <= 0 ? getString(R.string.genre_unknown_year)
                : getString(R.string.genre_decade, (year / 10) * 10);
    }

    private void showAlbums() {
        List<Serializable> rows = new ArrayList<>();
        String group = getArguments().getString(ARG_GROUP);
        if (group != null) {
            int tab = getArguments().getInt(ARG_GROUP_TAB);
            for (Entry album : albums) if (group.equals(groupName(album, tab))) rows.add(album);
        } else if (selectedTab == ALBUMS) {
            rows.addAll(albums);
        } else {
            Map<String, List<Entry>> sorted = selectedTab == YEARS
                    ? new TreeMap<>(java.util.Collections.reverseOrder()) : new TreeMap<>();
            for (Entry album : albums) {
                String key = groupName(album, selectedTab);
                List<Entry> entries = sorted.get(key);
                if (entries == null) { entries = new ArrayList<>(); sorted.put(key, entries); }
                entries.add(album);
            }
            groups = sorted;
            rows.addAll(groups.keySet());
        }
        recycler.setAdapter(new BrowserAdapter(rows));
        recycler.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        setEmpty(rows.isEmpty());
    }

    @Override
    public void onItemClicked(UpdateView<Serializable> view, Serializable item) {
        Bundle args = new Bundle();
        SubsonicFragment next;
        if (item instanceof Entry) {
            Entry album = (Entry) item;
            next = new SelectDirectoryFragment();
            args.putString(Constants.INTENT_EXTRA_NAME_ID, album.getId());
            args.putString(Constants.INTENT_EXTRA_NAME_NAME, album.getTitle());
            args.putSerializable(Constants.INTENT_EXTRA_NAME_DIRECTORY, album);
        } else {
            GenreBrowserFragment browser = new GenreBrowserFragment();
            // Share the already-loaded snapshot; returning keeps the original tab and scroll position.
            browser.albums = albums;
            next = browser;
            args.putString(ARG_GENRE, genre);
            args.putString(ARG_GROUP, (String) item);
            args.putInt(ARG_GROUP_TAB, selectedTab);
        }
        next.setArguments(args);
        replaceFragment(next, true);
    }

    @Override public void onCreateContextMenu(Menu menu, MenuInflater inflater, UpdateView<Serializable> view, Serializable item) {}
    @Override public boolean onContextItemSelected(MenuItem menu, UpdateView<Serializable> view, Serializable item) { return false; }

    @Override
    public void onDestroyView() {
        if (shuffleTask != null) { shuffleTask.cancel(); shuffleTask = null; }
        if (loadTask != null) { loadTask.cancel(); loadTask = null; }
        recycler = null;
        super.onDestroyView();
    }

    private class BrowserAdapter extends SectionAdapter<Serializable> {
        BrowserAdapter(List<Serializable> rows) {
            super(GenreBrowserFragment.this.context, rows);
            setOnItemClickedListener(GenreBrowserFragment.this);
        }
        @Override public int getItemViewType(Serializable row) { return row instanceof Entry ? 1 : 2; }
        @Override public UpdateView.UpdateViewHolder onCreateSectionViewHolder(ViewGroup parent, int type) {
            UpdateView view = type == 1 ? new AlbumView(context, false) : new GenreGroupView(context, getImageLoader());
            View more = view.findViewById(R.id.item_more);
            if (more != null) more.setVisibility(View.GONE);
            return new UpdateView.UpdateViewHolder(view);
        }
        @Override public void onBindViewHolder(UpdateView.UpdateViewHolder holder, Serializable row, int type) {
            if (type == 1) ((AlbumView) holder.getUpdateView()).setObject((Entry) row, getImageLoader());
            else {
                List<Entry> members = groups.get((String) row);
                Entry representative = null;
                for (Entry album : members) {
                    if (album.getCoverArt() != null && !album.getCoverArt().isEmpty()) {
                        representative = album;
                        break;
                    }
                }
                GenreGroupView view = (GenreGroupView) holder.getUpdateView();
                view.setObject((String) row, representative);
                view.setAlbumCount(members.size());
            }
        }
    }
}
