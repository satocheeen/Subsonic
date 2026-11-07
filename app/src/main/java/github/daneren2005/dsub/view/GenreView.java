/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package github.daneren2005.dsub.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;
import android.util.Log;
import java.util.List;
import java.util.Map;
import github.daneren2005.dsub.domain.MusicDirectory.Entry;
import github.daneren2005.dsub.service.MusicServiceFactory;
import github.daneren2005.dsub.util.ImageLoader;
import github.daneren2005.dsub.util.SilentBackgroundTask;
import github.daneren2005.dsub.R;
import github.daneren2005.dsub.domain.Genre;

public class GenreView extends UpdateView<Genre> {
	private static final String TAG = GenreView.class.getSimpleName();

	private TextView titleView;
	private TextView songsView;
	private TextView albumsView;

	private final ImageLoader imageLoader;
	private final Map<String, Entry> covers;

	public GenreView(Context context, ImageLoader imageLoader, Map<String, Entry> covers) {
		super(context, false);
		LayoutInflater.from(context).inflate(R.layout.genre_cell_item, this, true);

		this.imageLoader = imageLoader;
		this.covers = covers;
		coverArtView = findViewById(R.id.genre_art);
		titleView = (TextView) findViewById(R.id.genre_name);
		songsView = (TextView) findViewById(R.id.genre_songs);
		albumsView = (TextView) findViewById(R.id.genre_albums);
	}

	public void setObjectImpl(Genre genre) {
		titleView.setText(genre.getName());
		loadCover(genre);

		if(genre.getAlbumCount() != null) {
			songsView.setVisibility(View.VISIBLE);
			albumsView.setVisibility(View.VISIBLE);
			songsView.setText(context.getResources().getString(R.string.select_genre_songs, genre.getSongCount()));
			albumsView.setText(context.getResources().getString(R.string.select_genre_albums, genre.getAlbumCount()));
		} else {
			songsView.setVisibility(View.GONE);
			albumsView.setVisibility(View.GONE);
		}
	}

    private void loadCover(final Genre genre) {
        ((ImageView) coverArtView).setImageResource(R.drawable.unknown_album);
        coverArtView.setContentDescription(null);
        if (covers.containsKey(genre.getName())) {
            showCover(covers.get(genre.getName()));
            return;
        }
        // Only visible cards request a representative album; the genre list can appear immediately.
        imageTask = new SilentBackgroundTask<Void>(context) {
            private Entry album;
            @Override
            protected Void doInBackground() throws Exception {
                List<Entry> entries = MusicServiceFactory.getMusicService(context)
                        .getAlbumList("genres", genre.getName(), 1, 0, false, context, this).getChildren();
                if (!entries.isEmpty()) album = entries.get(0);
                return null;
            }
            @Override
            protected void done(Void result) {
                covers.put(genre.getName(), album);
                // A recycled card must never receive the previous genre's jacket.
                if (item == genre) showCover(album);
            }
            @Override
            protected void error(Throwable error) {
                // Artwork is optional: leave the card usable, without an error dialog.
                Log.w(TAG, "Could not load genre cover", error);
            }
        };
        imageTask.execute();
    }

    private void showCover(Entry album) {
        if (album != null && album.getCoverArt() != null) {
            imageTask = imageLoader.loadImage(coverArtView, album, false, true);
        }
    }

}
