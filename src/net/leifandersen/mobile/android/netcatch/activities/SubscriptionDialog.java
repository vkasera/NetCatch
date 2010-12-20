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

import java.util.ArrayList;

import net.leifandersen.mobile.android.netcatch.R;
import net.leifandersen.mobile.android.netcatch.providers.Episode;
import net.leifandersen.mobile.android.netcatch.providers.Show;
import net.leifandersen.mobile.android.netcatch.providers.ShowsProvider;
import net.leifandersen.mobile.android.netcatch.services.RSSService;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SubscriptionDialog extends Dialog {

	private Context ctx;
	private EditText mEditFeed;
	private String newFeed;
	private Dialog progressDialog;
	
	public SubscriptionDialog(Context context) {
		super(context);
		ctx = context;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Set the view
		LayoutInflater inflater = getLayoutInflater();
		View layout = inflater.inflate(R.layout.subscription_feed_dialog, null);
		setContentView(layout);
		
		// Get the textbox, for retreiving text later.
		mEditFeed = (EditText)layout.findViewById(R.id.edit_text);
		
		// Set up other properties
		setTitle(ctx.getString(R.string.enter_feed));
		setCancelable(true);
		
		// Set up the OK button click
		((Button)findViewById(R.id.ok_button)).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// Get the RSS feed the user entered
				newFeed = mEditFeed.getText().toString();

				// Get the feed's data
				// Set the broadcast reciever
				BroadcastReceiver finishedReceiver = new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {							
						progressDialog.cancel();
					}
				};
				ctx.registerReceiver(finishedReceiver, new IntentFilter(RSSService.RSSFINISH + newFeed));

				// Set up the failed dialog
				BroadcastReceiver failedReciever = new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {
						progressDialog.cancel();
						Toast.makeText(ctx, "Failed to fetch feed", Toast.LENGTH_LONG);
					}
				};
				ctx.registerReceiver(failedReciever, new IntentFilter(RSSService.RSSFAILED + newFeed));
				
				// Show a waiting dialog (that can be canceled)
				progressDialog =
					ProgressDialog.show(ctx,
							"", ctx.getString(R.string.getting_show_details));
				progressDialog.setCancelable(true);
				progressDialog.show();
				
				// Start the service
				Intent service = new Intent();
				service.putExtra(RSSService.FEED, newFeed);
				service.setClass(ctx, RSSService.class);
				ctx.startService(service);
				dismiss();
			}
		});
		
		// Set up the cancle button
		((Button)findViewById(R.id.cancel_button)).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
	}
}
