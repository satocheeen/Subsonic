package github.daneren2005.dsub.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import github.daneren2005.dsub.R;
import github.daneren2005.dsub.domain.MusicDirectory.Entry;
import github.daneren2005.dsub.util.ImageLoader;

/** Artist and decade rows share the album row's artwork size, spacing and typography. */
public class GenreGroupView extends UpdateView2<String, Entry> {
    private final ImageLoader imageLoader;
    private final TextView title;
    private final TextView albumCount;

    public GenreGroupView(Context context, ImageLoader imageLoader) {
        super(context, false);
        this.imageLoader = imageLoader;
        LayoutInflater.from(context).inflate(R.layout.album_list_item, this, true);
        coverArtView = findViewById(R.id.album_coverart);
        title = (TextView) findViewById(R.id.album_title);
        albumCount = (TextView) findViewById(R.id.album_artist);
        findViewById(R.id.item_more).setVisibility(View.GONE);
    }

    @Override
    protected void setObjectImpl(String name, Entry representative) {
        title.setText(name);
        // A missing cover uses ImageLoader's standard unknown-album artwork.
        imageTask = imageLoader.loadImage(coverArtView, representative, false, true);
    }

    public void setAlbumCount(int count) {
        albumCount.setText(context.getString(R.string.select_genre_albums, count));
    }
}
