/***************************************************************************
 *   Copyright 2005-2009 Last.fm Ltd.                                      *
 *   Portions contributed by Casey Link, Lukasz Wisniewski,                *
 *   Mike Jennings, and Michael Novak Jr.                                  *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.         *
 ***************************************************************************/
package fm.last.android.activity;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewFlipper;
import android.widget.AdapterView.OnItemClickListener;
import fm.last.android.Amazon;
import fm.last.android.AndroidLastFmServerFactory;
import fm.last.android.LastFMApplication;
import fm.last.android.R;
import fm.last.android.activity.Event.EventActivityResult;
import fm.last.android.adapter.EventListAdapter;
import fm.last.android.adapter.ListAdapter;
import fm.last.android.adapter.ListEntry;
import fm.last.android.adapter.NotificationAdapter;
import fm.last.android.player.IRadioPlayer;
import fm.last.android.utils.ImageCache;
import fm.last.android.utils.UserTask;
import fm.last.android.widget.TabBar;
import fm.last.api.Artist;
import fm.last.api.Event;
import fm.last.api.ImageUrl;
import fm.last.api.LastFmServer;
import fm.last.api.Tag;
import fm.last.api.User;

/**
 * @author Jono Cole <jono@last.fm>
 * 
 */
public class Metadata extends Activity {

	private String mBio;
	private ListAdapter mSimilarAdapter;
	private ListAdapter mFanAdapter;
	private ListAdapter mTagAdapter;
	private String mArtistName = "";
	private String mTrackName = "";

	private BaseAdapter mEventAdapter;

	private ImageCache mImageCache;
	private EventActivityResult mOnEventActivityResult;
	private boolean mIsPlaying = false;

	TextView mTextView;
	TabBar mTabBar;
	ViewFlipper mViewFlipper;
	WebView mWebView;
	ListView mSimilarList;
	ListView mTagList;
	ListView mFanList;
	ListView mEventList;
	LastFmServer mServer = AndroidLastFmServerFactory.getServer();

	public Metadata() {
		super();
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.metadata);

		mArtistName = getIntent().getStringExtra("artist");
		mTrackName = getIntent().getStringExtra("track");

		mTabBar = (TabBar) findViewById(R.id.TabBar);
		mViewFlipper = (ViewFlipper) findViewById(R.id.ViewFlipper);
		mWebView = (WebView) findViewById(R.id.webview);
		mSimilarList = (ListView) findViewById(R.id.similar_list_view);
		mTagList = (ListView) findViewById(R.id.tags_list_view);
		mFanList = (ListView) findViewById(R.id.listeners_list_view);
		mEventList = (ListView) findViewById(R.id.events_list_view);

		mTabBar.setViewFlipper(mViewFlipper);
		mTabBar.addTab(getString(R.string.metadata_bio), R.drawable.bio);
		mTabBar.addTab(getString(R.string.metadata_similar), R.drawable.similar_artists);
		mTabBar.addTab(getString(R.string.metadata_tags), R.drawable.tags);
		mTabBar.addTab(getString(R.string.metadata_events), R.drawable.events);
		mTabBar.addTab(getString(R.string.metadata_Fans), R.drawable.top_listeners);

		populateMetadata();

		if (getIntent().hasExtra("show_events"))
			mTabBar.setActive(R.drawable.events);

		mIsPlaying = false;

		LastFMApplication.getInstance().bindService(new Intent(LastFMApplication.getInstance(), fm.last.android.player.RadioPlayerService.class),
				new ServiceConnection() {
					public void onServiceConnected(ComponentName comp, IBinder binder) {
						IRadioPlayer player = IRadioPlayer.Stub.asInterface(binder);
						try {
							mIsPlaying = player.isPlaying();
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						LastFMApplication.getInstance().unbindService(this);
					}

					public void onServiceDisconnected(ComponentName comp) {
					}
				}, Context.BIND_AUTO_CREATE);

	}

	@Override
	public void onResume() {
		super.onResume();
		LastFMApplication.getInstance().tracker.trackPageView("/Metadata");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.player, menu);

		MenuItem changeView = menu.findItem(R.id.info_menu_item);
		changeView.setTitle(getString(R.string.action_nowplaying));
		changeView.setIcon(R.drawable.view_artwork);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.buy_menu_item).setEnabled(Amazon.getAmazonVersion(this) > 0);
		menu.findItem(R.id.info_menu_item).setEnabled(mIsPlaying);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent;

		switch (item.getItemId()) {
		case R.id.info_menu_item:
			Intent i = new Intent(this, Player.class);
			startActivity(i);
			finish();
			break;
		case R.id.buy_menu_item:
			Amazon.searchForTrack(this, mArtistName, mTrackName);
			break;
		case R.id.share_menu_item:
			intent = new Intent(this, ShareResolverActivity.class);
			intent.putExtra(Share.INTENT_EXTRA_ARTIST, mArtistName);
			intent.putExtra(Share.INTENT_EXTRA_TRACK, mTrackName);
			startActivity(intent);
			break;
		case R.id.playlist_menu_item:
			intent = new Intent(this, AddToPlaylist.class);
			intent.putExtra(Share.INTENT_EXTRA_ARTIST, mArtistName);
			intent.putExtra(Share.INTENT_EXTRA_TRACK, mTrackName);
			startActivity(intent);
			break;
		case R.id.tag_menu_item:
			intent = new Intent(this, fm.last.android.activity.Tag.class);
			intent.putExtra("lastfm.artist", mArtistName);
			intent.putExtra("lastfm.track", mTrackName);
			startActivity(intent);
			break;
		default:
			break;
		}

		return super.onOptionsItemSelected(item);
	}

	private void populateMetadata() {

		// Order bio / event loading depending on whether
		// on tour button was clicked
		if (getIntent().hasExtra("show_events")) {
			new LoadEventsTask().execute((Void) null);
			new LoadBioTask().execute((Void) null);
		} else {
			new LoadBioTask().execute((Void) null);
			new LoadEventsTask().execute((Void) null);
		}

		new LoadSimilarTask().execute((Void) null);
		new LoadListenersTask().execute((Void) null);
		new LoadTagsTask().execute((Void) null);
		new LoadEventsTask().execute((Void) null);

		mTabBar.setActive(R.drawable.bio);
	}

	private ImageCache getImageCache() {
		if (mImageCache == null) {
			mImageCache = new ImageCache();
		}
		return mImageCache;
	}

	private class LoadBioTask extends UserTask<Void, Void, Boolean> {
		@Override
		public void onPreExecute() {
			mWebView.loadData(getString(R.string.common_loading), "text/html", "utf-8");
		}

		@Override
		public Boolean doInBackground(Void... params) {
			Artist artist;
			boolean success = false;

			try {
				String lang = Locale.getDefault().getLanguage();
				if (lang.equalsIgnoreCase("de")) {
					artist = mServer.getArtistInfo(mArtistName, null, lang);
				} else {
					artist = mServer.getArtistInfo(mArtistName, null, null);
				}
				if (artist.getBio().getContent() == null || artist.getBio().getContent().trim().length() == 0) {
					// no bio in current locale -> get the English bio
					artist = mServer.getArtistInfo(mArtistName, null, null);
				}
				String imageURL = "";
				for (ImageUrl image : artist.getImages()) {
					if (image.getSize().contentEquals("large")) {
						imageURL = image.getUrl();
						break;
					}
				}

				String listeners = "";
				String plays = "";
				try {
					NumberFormat nf = NumberFormat.getInstance();
					listeners = nf.format(Integer.parseInt(artist.getListeners()));
					plays = nf.format(Integer.parseInt(artist.getPlaycount()));
				} catch (NumberFormatException e) {
				}

				mBio = "<html><body style='margin:0; padding:0; color:black; background: white; font-family: Helvetica; font-size: 11pt;'>"
						+ "<div style='padding:17px; margin:0; top:0px; left:0px; position:absolute;'>" + "<img src='" + imageURL
						+ "' style='margin-top: 4px; float: left; margin-right: 0px; margin-bottom: 14px; width:64px; border:1px solid gray; padding: 1px;'/>"
						+ "<div style='margin-left:84px; margin-top:3px'>" + "<span style='font-size: 15pt; font-weight:bold; padding:0px; margin:0px;'>"
						+ mArtistName + "</span><br/>" + "<span style='color:gray; font-weight: normal; font-size: 10pt;'>" + listeners + " "
						+ getString(R.string.metadata_listeners) + "<br/>" + plays + " " + getString(R.string.metadata_plays) + "</span></div>"
						+ "<br style='clear:both;'/>" + formatBio(artist.getBio().getContent()) + "</div></body></html>";

				success = true;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return success;
		}

		private String formatBio(String wikiText) {
			// last.fm api returns the wiki text without para formatting,
			// correct that:
			return wikiText.replaceAll("\\n+", "<br>");
		}

		@Override
		public void onPostExecute(Boolean result) {
			if (result) {
				try {
					mWebView.loadDataWithBaseURL(null, new String(mBio.getBytes(), "utf-8"), // need
																								// to
																								// do
																								// this,
																								// but
																								// is
																								// there
																								// a
																								// better
																								// way?
							"text/html", "utf-8", null);
					// request focus to make the web view immediately scrollable
					mWebView.requestFocus();
					return;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			mWebView.loadData(getString(R.string.metadata_nobio), "text/html", "utf-8");
		}
	}

	private class LoadSimilarTask extends UserTask<Void, Void, ArrayList<ListEntry>> {

		@Override
		public void onPreExecute() {
			mSimilarList.setOnItemClickListener(null);
			mSimilarList.setAdapter(new NotificationAdapter(Metadata.this, NotificationAdapter.LOAD_MODE, getString(R.string.common_loading)));
		}

		@Override
		public ArrayList<ListEntry> doInBackground(Void... params) {

			try {
				Artist[] similar = mServer.getSimilarArtists(mArtistName, null);
				if (similar.length == 0)
					return null;

				ArrayList<ListEntry> iconifiedEntries = new ArrayList<ListEntry>();
				for (int i = 0; i < ((similar.length < 10) ? similar.length : 10); i++) {
					ListEntry entry = new ListEntry(similar[i], R.drawable.artist_icon, similar[i].getName(), similar[i].getImages()[0].getUrl(),
							R.drawable.list_icon_station);
					iconifiedEntries.add(entry);
				}
				return iconifiedEntries;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public void onPostExecute(ArrayList<ListEntry> iconifiedEntries) {
			if (iconifiedEntries != null) {
				mSimilarAdapter = new ListAdapter(Metadata.this, getImageCache());
				mSimilarAdapter.setSourceIconified(iconifiedEntries);
				mSimilarList.setAdapter(mSimilarAdapter);
				mSimilarList.setOnItemClickListener(new OnItemClickListener() {

					public void onItemClick(AdapterView<?> l, View v, int position, long id) {
						Artist artist = (Artist) mSimilarAdapter.getItem(position);
						mSimilarAdapter.enableLoadBar(position);
						LastFMApplication.getInstance().playRadioStation(Metadata.this, "lastfm://artist/" + Uri.encode(artist.getName()) + "/similarartists",
								false);
					}

				});
			} else {
				mSimilarList.setAdapter(new NotificationAdapter(Metadata.this, NotificationAdapter.INFO_MODE, getString(R.string.metadata_nosimilar)));
			}
		}
	}

	private class LoadListenersTask extends UserTask<Void, Void, ArrayList<ListEntry>> {

		@Override
		public void onPreExecute() {
			mFanList.setAdapter(new NotificationAdapter(Metadata.this, NotificationAdapter.LOAD_MODE, getString(R.string.common_loading)));
			mFanList.setOnItemClickListener(null);
		}

		@Override
		public ArrayList<ListEntry> doInBackground(Void... params) {
			try {
				User[] fans = mServer.getTrackTopFans(mTrackName, mArtistName, null);
				if (fans.length == 0)
					return null;
				ArrayList<ListEntry> iconifiedEntries = new ArrayList<ListEntry>();
				for (int i = 0; i < ((fans.length < 10) ? fans.length : 10); i++) {
					ListEntry entry = new ListEntry(fans[i], R.drawable.profile_unknown, fans[i].getName(), fans[i].getImages()[0].getUrl(),
							R.drawable.list_icon_arrow);
					iconifiedEntries.add(entry);
				}
				return iconifiedEntries;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public void onPostExecute(ArrayList<ListEntry> iconifiedEntries) {
			if (iconifiedEntries != null) {
				mFanAdapter = new ListAdapter(Metadata.this, getImageCache());
				mFanAdapter.setSourceIconified(iconifiedEntries);
				mFanList.setAdapter(mFanAdapter);
				mFanList.setOnItemClickListener(new OnItemClickListener() {
					public void onItemClick(AdapterView<?> l, View v, int position, long id) {
						User user = (User) mFanAdapter.getItem(position);
						Intent profileIntent = new Intent(Metadata.this, fm.last.android.activity.Profile.class);
						profileIntent.putExtra("lastfm.profile.username", user.getName());
						startActivity(profileIntent);
					}
				});
			} else {
				mFanList.setAdapter(new NotificationAdapter(Metadata.this, NotificationAdapter.INFO_MODE, getString(R.string.metadata_nofans)));
			}
		}
	}

	private class LoadTagsTask extends UserTask<Void, Void, ArrayList<ListEntry>> {

		@Override
		public void onPreExecute() {
			mTagList.setAdapter(new NotificationAdapter(Metadata.this, NotificationAdapter.LOAD_MODE, getString(R.string.common_loading)));
			mTagList.setOnItemClickListener(null);
		}

		@Override
		public ArrayList<ListEntry> doInBackground(Void... params) {
			try {
				Tag[] tags = mServer.getTrackTopTags(mArtistName, mTrackName, null);
				if (tags.length == 0)
					return null;
				ArrayList<ListEntry> iconifiedEntries = new ArrayList<ListEntry>();
				for (int i = 0; i < ((tags.length < 10) ? tags.length : 10); i++) {
					ListEntry entry = new ListEntry(tags[i], -1, tags[i].getName(), R.drawable.list_icon_station);
					iconifiedEntries.add(entry);
				}
				return iconifiedEntries;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public void onPostExecute(ArrayList<ListEntry> iconifiedEntries) {
			if (iconifiedEntries != null) {
				mTagAdapter = new ListAdapter(Metadata.this, getImageCache());
				mTagAdapter.setSourceIconified(iconifiedEntries);
				mTagList.setAdapter(mTagAdapter);
				mTagList.setOnItemClickListener(new OnItemClickListener() {

					public void onItemClick(AdapterView<?> l, View v, int position, long id) {
						Tag tag = (Tag) mTagAdapter.getItem(position);
						mTagAdapter.enableLoadBar(position);
						LastFMApplication.getInstance().playRadioStation(Metadata.this, "lastfm://globaltags/" + Uri.encode(tag.getName()), false);
					}

				});
			} else {
				mTagList.setAdapter(new NotificationAdapter(Metadata.this, NotificationAdapter.INFO_MODE, getString(R.string.metadata_notags)));
			}
		}
	}

	/**
	 * This load task is slightly bigger as it has to handle OnTour indicator
	 * and Metadata's event list. The main problem here is new events must be
	 * downloaded on track change even if the user is viewing old events in the
	 * metadata view.
	 * 
	 * @author Lukasz Wisniewski
	 */
	private class LoadEventsTask extends UserTask<Void, Void, Boolean> {

		/**
		 * New adapter representing events data
		 */
		private BaseAdapter mNewEventAdapter;

		@Override
		public void onPreExecute() {
			mEventList.setOnItemClickListener(null);
			mEventList.setAdapter(new NotificationAdapter(Metadata.this, NotificationAdapter.LOAD_MODE, getString(R.string.common_loading)));
		}

		@Override
		public Boolean doInBackground(Void... params) {
			boolean result = false;

			mNewEventAdapter = new EventListAdapter(Metadata.this);

			try {
				Event[] events = mServer.getArtistEvents(mArtistName);
				((EventListAdapter) mNewEventAdapter).setEventsSource(events);
				if (events.length > 0)
					result = true;

			} catch (IOException e) {
				e.printStackTrace();
			}

			if (!result) {
				mNewEventAdapter = new NotificationAdapter(Metadata.this, NotificationAdapter.INFO_MODE, getString(R.string.metadata_noevents));
				mEventList.setOnItemClickListener(null);
			}

			return result;
		}

		@Override
		public void onPostExecute(Boolean result) {
			mEventAdapter = mNewEventAdapter;
			mEventList.setAdapter(mEventAdapter);
			if (result) {
				mEventList.setOnItemClickListener(mEventOnItemClickListener);
			}
		}
	}

	private OnItemClickListener mEventOnItemClickListener = new OnItemClickListener() {

		public void onItemClick(final AdapterView<?> parent, final View v, final int position, long id) {

			final Event event = (Event) parent.getAdapter().getItem(position);

			Intent intent = fm.last.android.activity.Event.intentFromEvent(Metadata.this, event);
			try {
				Event[] events = mServer.getUserEvents((LastFMApplication.getInstance().session).getName());
				for (Event e : events) {
					// System.out.printf("Comparing id %d (%s) to %d (%s)\n",e.getId(),e.getTitle(),event.getId(),event.getTitle());
					if (e.getId() == event.getId()) {
						// System.out.printf("Matched! Status: %s\n",
						// e.getStatus());
						intent.putExtra("lastfm.event.status", e.getStatus());
						break;
					}

				}
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			mOnEventActivityResult = new EventActivityResult() {
				public void onEventStatus(int status) {
					event.setStatus(String.valueOf(status));
					mOnEventActivityResult = null;
				}
			};

			startActivityForResult(intent, 0);
		}

	};

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 0 && resultCode == RESULT_OK) {
			int status = data.getExtras().getInt("status", -1);
			if (mOnEventActivityResult != null && status != -1) {
				mOnEventActivityResult.onEventStatus(status);
			}
		}
	}

}
