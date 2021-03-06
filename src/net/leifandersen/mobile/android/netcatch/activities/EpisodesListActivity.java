/*Copyright 2010 NetCatch Team
 *Licensed under the Apache License, Version 2.0 (the "License");
 *you may not use this file except in compliance with the License.
 *You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing, software
 *distributed under the License is distributed on an "AS IS" BASIS,
 *WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *See the License for the specific language governing permissions and
 *limitations under the License.
 */
package net.leifandersen.mobile.android.netcatch.activities;

import java.io.File;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import net.leifandersen.mobile.android.netcatch.R;
import net.leifandersen.mobile.android.netcatch.other.GlobalVars;
import net.leifandersen.mobile.android.netcatch.other.ThemeTools;
import net.leifandersen.mobile.android.netcatch.other.Tools;
import net.leifandersen.mobile.android.netcatch.providers.Episode;
import net.leifandersen.mobile.android.netcatch.providers.ShowsProvider;
import net.leifandersen.mobile.android.netcatch.services.MediaDownloadService;
import net.leifandersen.mobile.android.netcatch.services.RSSService;
import net.leifandersen.mobile.android.netcatch.services.UnsubscribeService;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 * @author Leif Andersen
 *
 */
public class EpisodesListActivity extends ListActivity {

	private GlobalVars globalVars;
	
	private static final class ViewHolder {
		TextView title;
		//TextView description;
		TextView date;
	}

	private class EpisodeAdapter extends ArrayAdapter<Episode> {

		LayoutInflater mInflater;

		public EpisodeAdapter(Context context) {
			super(context, R.layout.episode);
			mInflater = getLayoutInflater();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if(convertView == null) {
				convertView = mInflater.inflate(R.layout.episode, null);
				holder = new ViewHolder();
				holder.title = (TextView)convertView.findViewById(R.id.episode_title);
				//holder.description = (TextView)convertView.findViewById(R.id.eli_description);
				holder.date = (TextView)convertView.findViewById(R.id.episode_date);
				convertView.setTag(holder);
			}
			else
				holder = (ViewHolder)convertView.getTag();

			Episode episode = getItem(position);
			
			if (episode != null) {
				holder.title.setText(episode.getTitle());
				holder.title.setTypeface(globalVars.getVeraBold());
	
				String dateText = "Published " + (new Date(episode.getDate())).toLocaleString();
				holder.date.setText(dateText);
				holder.date.setTypeface(globalVars.getVera());
			}
			
			//holder.title.setText(episode.getTitle());
			//holder.description.setText(episode.getDescription());
			//holder.date.setText(episode.getDate());
			//holder.date.setTag(new Date(episode.getDate()).toString());

			return convertView;
		}
	}

	public static final String SHOW_ID = "show_id";
	public static final String SHOW_NAME = "show_name";
	public static final String SHOW_FEED = "show_feed";
	
	private LinearLayout background;
	private FrameLayout header;
	private TextView headerText;
	private String mShowName;
	private long mShowID;
	private EpisodeAdapter mAdapter;
	private static final int NEW_FEED = 1;
	private static final int UNSUBSCRIBE = 2;
	private static final int IMPLICIT_NEW_FEED = 3;
	private List<Episode> mEpisodes;
	private SharedPreferences mSharedPrefs;
	private String mFeed;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.episodes_list);
		mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		globalVars = (GlobalVars)getApplicationContext();
		
		if(getIntent().getDataString() == null)
			explicitInitiate();
		else
			implicitInitiate();

		// Set up the view
		background = (LinearLayout)findViewById(R.id.background);
		header = (FrameLayout)findViewById(R.id.header);
		headerText = (TextView)header.findViewById(R.id.title_text);
		headerText.setTypeface(globalVars.getVeraBold());
		
		int x = mSharedPrefs.getInt("theme_color", -1);
		if(x != -1)
			ThemeTools.setColorOverlay(new PorterDuffColorFilter(x, 
					PorterDuff.Mode.MULTIPLY), background, header);

		// Set up the refresh button
		findViewById(R.id.btn_refresh).setOnClickListener(
				new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent refresh = new Intent();
				refresh.putExtra(RSSService.FEED, mFeed);
				refresh.putExtra(RSSService.ID, mShowID);
			}
		});
		
		// Set the List Adapter
		refreshList();

		// Register the list for context menus
		registerForContextMenu(getListView());
	}

	private void implicitInitiate() {
		mFeed = getIntent().getDataString();
		showDialog(IMPLICIT_NEW_FEED);
	}

	private void explicitInitiate() {
		// Get the show name
		// If no show was passed in, the activity was called poorly, abort.
		Bundle b = getIntent().getExtras();
		if (b == null)
			throw new IllegalArgumentException("No Bundle Given");


		mShowID = b.getLong(SHOW_ID, -1);
		mShowName = b.getString(SHOW_NAME);
		mFeed = b.getString(SHOW_FEED);
		
		if(mShowID < 0 || mShowName == null || mFeed == null)
			throw new IllegalArgumentException("No show ID, name " +
					"or feed given");
	}

	@Override
	protected void onRestart() {
		super.onResume();

		// Set up the color
		int x = mSharedPrefs.getInt("theme_color", -1);
		if(x != -1)
			ThemeTools.setColorOverlay(new PorterDuffColorFilter(x, 
					PorterDuff.Mode.MULTIPLY),
					background, header);

		// Refresh the list
		refreshList();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Episode e = mEpisodes.get(position);
		Intent i = new Intent();
		i.putExtra(EpisodeActivity.ID, e.getId()); // TODO
		i.setClass(this, EpisodeActivity.class);
		startActivity(i);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.episodes_options, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent activity;
		switch (item.getItemId()) {
		case R.id.home_item:
			activity = new Intent();
			activity.setClass(this, NCMain.class);
			startActivity(activity);
			return true;
		case R.id.unsubscribe_item:
			showDialog(UNSUBSCRIBE);
			break;
		case R.id.new_show_item:
			showDialog(NEW_FEED);
			return true;
		case R.id.preferences_item:
			activity = new Intent();
			activity.setClass(this, Preferences.class);
			startActivity(activity);
			return true;
		}
		return false;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		// If menuInfo is null abort
		if(menuInfo == null)
			return;
		MenuInflater inflater = getMenuInflater();
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		Episode episode = mEpisodes.get((int)info.id);
		if(TextUtils.isEmpty(episode.getMedia())) {
			if(episode.isPlayed())
				inflater.inflate(
						R.menu.episodes_context_not_downloaded_played, menu);
			else
				inflater.inflate(
						R.menu.episodes_context_not_downloaded_unplayed, menu);
		}
		else {
			if(episode.isPlayed())
				inflater.inflate(
						R.menu.episodes_context_downloaded_played, menu);
			else
				inflater.inflate(
						R.menu.episodes_context_downloaded_unplayed, menu);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = 
			(AdapterContextMenuInfo)item.getMenuInfo();
		final Episode episode = mEpisodes.get((int)info.id);
		switch(item.getItemId()) {
		case R.id.download:
			if(Environment.MEDIA_MOUNTED.equals(
					Environment.getExternalStorageState()) && 
					episode.getMediaUrl() != null) {
				// Calculate the download directory
				// Setup files,  save data
				int slashIndex = episode.getMediaUrl().lastIndexOf('/');
				String filename =
					episode.getMediaUrl().substring(slashIndex + 1);
				SharedPreferences pref =
					PreferenceManager.getDefaultSharedPreferences(this);
				final File file =
					new File(Environment.getExternalStorageDirectory(),
							pref.getString(Preferences.DOWNLOAD_LOCATION,
							"PODCASTS")
							+ "/" + mShowName + "/" + filename);

				// Initiate the download
				Intent service = new Intent();
				service.setClass(this, MediaDownloadService.class)
				.putExtra(MediaDownloadService.MEDIA_ID, episode.getId())
				.putExtra(MediaDownloadService.MEDIA_URL,episode.getMediaUrl())
				.putExtra(MediaDownloadService.MEDIA_LOCATION, file.getPath())
				.putExtra(MediaDownloadService.BACKGROUND_UPDATE, false);
				startService(service);
				return true;
			}
			else
				return false;
		case R.id.delete:
			if(Environment.MEDIA_MOUNTED.equals(
					Environment.getExternalStorageState()) && 
					episode.getMedia() != null) {
				File file = new File(episode.getMedia());
				if(!file.delete()) {
					Toast.makeText(this, R.string.could_not_delete,
							Toast.LENGTH_LONG).show();
				}
				// Clear out the location in the episode object
				episode.setMedia(null);

				// Remove it form the database too.
				ContentValues v = new ContentValues();
				v.put(ShowsProvider.MEDIA, "");
				getContentResolver().update(Uri.parse(
						ShowsProvider.EPISODES_CONTENT_URI + "/" 
						+ episode.getId()), v, null, null);
				return true;
			}
			else
				return false;
		case R.id.play:
			Intent intent = new Intent();
			intent.putExtra(EpisodeActivity.ID, episode.getId());
			startActivity(intent);
		}
		return false;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		return onCreateDialog(id, new Bundle());
	}

	@Override
	protected Dialog onCreateDialog(int id, Bundle args) {
		Dialog dialog = null;
		switch(id) {
		case NEW_FEED:
			dialog = Tools.createSubscriptionDialog(this);
			break;
		case UNSUBSCRIBE:
			dialog = Tools.createUnsubscribeDialog(this,
					new DialogInterface.OnClickListener() {
				BroadcastReceiver finishedReceiver;
				ProgressDialog progressDialog;		
				public void onClick(DialogInterface dialog, int id) {
					// Register the broadcast receiver
					finishedReceiver = new BroadcastReceiver() {
						@Override
						public void onReceive(Context context, Intent intent) {
							// Clean up the mess that was made.
							unregisterReceiver(finishedReceiver);
							progressDialog.cancel();
							refreshList();
							finish();
						}
					};
					registerReceiver(finishedReceiver,
							new IntentFilter(UnsubscribeService.FINISHED
									+mShowID));

					// Pop up a dialog while waiting
					progressDialog =
						ProgressDialog.show(EpisodesListActivity.this, "",
								EpisodesListActivity.this.getString(
										R.string.unsubscribing_from_show)
										+ mShowName
										+ EpisodesListActivity.this.getString(
												R.string.end_quotation));
					progressDialog.setCancelable(false);
					progressDialog.show();

					// Unsubscribe from the show
					Intent service = new Intent();
					service.putExtra(UnsubscribeService.SHOW_ID, mShowID);
					service.setClass(EpisodesListActivity.this,
							UnsubscribeService.class);
					startService(service);
				}
			}, mShowName, mShowID);
			break;
		case IMPLICIT_NEW_FEED:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);

			// Set up the layout
			LayoutInflater inflater = 
				(LayoutInflater)getSystemService(
						Context.LAYOUT_INFLATER_SERVICE);
			View layout = inflater.inflate(R.layout.subscription_feed_dialog,
					null);
			final EditText editFeed = 
				(EditText)layout.findViewById(R.id.sfd_editText);
			editFeed.setText(mFeed);
			builder.setView(layout);
			builder.setCancelable(false);
			builder.setPositiveButton(getString(android.R.string.ok),
					new DialogInterface.OnClickListener() {
				BroadcastReceiver finishedReceiver;
				BroadcastReceiver failedReciever;
				ProgressDialog progressDialog;
				@Override
				public void onClick(DialogInterface dialog, int which) {
					final String newFeed = editFeed.getText().toString();

					// Get the feed's data
					// Set the broadcast reciever
					finishedReceiver = new BroadcastReceiver() {
						@Override
						public void onReceive(Context context, Intent intent) {
							// Clean up
							progressDialog.dismiss();
							unregisterReceiver(finishedReceiver);
							unregisterReceiver(failedReciever);

							// Refresh the list
							mShowID = intent.getLongExtra(RSSService.ID, -1);
							mShowName = intent.getStringExtra(RSSService.NAME);
							if(mShowID < 0) {
								finish();
								return;
							}
							refreshList();
						}
					};
					registerReceiver(finishedReceiver,
							new IntentFilter(RSSService.RSSFINISH + newFeed));

					// Set up the failed dialog
					failedReciever = new BroadcastReceiver() {
						@Override
						public void onReceive(Context context, Intent intent) {
							progressDialog.dismiss();
							Toast.makeText(EpisodesListActivity.this,
									"Failed to fetch feed", 
									Toast.LENGTH_LONG).show();
							unregisterReceiver(finishedReceiver);
							unregisterReceiver(failedReciever);
							finish();
						}
					};
					registerReceiver(failedReciever,
							new IntentFilter(RSSService.RSSFAILED + newFeed));

					// Show a waiting dialog (that can be canceled)
					progressDialog =
						ProgressDialog.show(EpisodesListActivity.this,
								"", getString(R.string.getting_show_details));
					progressDialog.setOnCancelListener(
							new DialogInterface.OnCancelListener() {
								@Override
								public void onCancel(DialogInterface dialog) {
									try {
										unregisterReceiver(finishedReceiver);
									} catch (Exception e) {
										Log.w("EpisodesListActivity",
												"finishedReceiver already " + 
												"unregistered");
									} try {
										unregisterReceiver(failedReciever);
									} catch (Exception e) {
										Log.w("EpisodesListActivity",
												"failedReceiver already " + 
												"unregistered");
									}
									finish();
								}
							});
					progressDialog.setCancelable(true);
					progressDialog.show();
					
					// Save the feed
					mFeed = newFeed;
					
					// Start the service
					Intent service = new Intent();
					service.putExtra(RSSService.FEED, newFeed);
					service.putExtra(RSSService.BACKGROUND_UPDATE, false);
					service.setClass(EpisodesListActivity.this,
							RSSService.class);
					startService(service);
				}
			});
			builder.setNegativeButton(getString(android.R.string.cancel),
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					finish();
					dialog.cancel();
				}
			});
			dialog = builder.create();
			break;
		default:
			dialog = null;
		}
		return dialog;
	}

	private void refreshList() {
		mAdapter = new EpisodeAdapter(this);
		setListAdapter(mAdapter);

		// Get a list of all of the elements.
		// Add the list to the adapter
		mEpisodes = new ArrayList<Episode>();
		Cursor c = managedQuery(Uri.parse(ShowsProvider.SHOWS_CONTENT_URI
				+ "/" + mShowID + "/episodes"), null, null, null, null);
		if (c.moveToLast())
			do {
				Episode ep = new Episode(
						c.getLong(c.getColumnIndex(ShowsProvider._ID)), mShowID,
						c.getString(c.getColumnIndex(ShowsProvider.TITLE)),
						c.getString(c.getColumnIndex(ShowsProvider.AUTHOR)),
						c.getString(c.getColumnIndex(ShowsProvider.DESCRIPTION))
						,
						c.getString(c.getColumnIndex(ShowsProvider.MEDIA)),
						c.getString(c.getColumnIndex(ShowsProvider.MEDIA_URL)),
						c.getLong(c.getColumnIndex(ShowsProvider.DATE)),
						c.getLong(c.getColumnIndex(ShowsProvider.BOOKMARK)),
						c.getLong(c.getColumnIndex(ShowsProvider.PLAYED)) == 
							ShowsProvider.IS_PLAYED);
				mAdapter.add(ep);
				mEpisodes.add(ep);
			} while (c.moveToPrevious());
	}
}
