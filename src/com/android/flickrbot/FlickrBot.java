/**
 *  FlickrBot: A Flickr upload client for Android
 *  Copyright (C) 2009 Matt Mets
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.android.flickrbot;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Android application that implements a Flickr upload client
 * 
 * @author Matt Mets
 */
public class FlickrBot extends Activity {
	Database m_database;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Init the database, as all routines will need it.
        if (m_database == null) {
        	m_database = new Database(this);
        }
        else {
        	System.out.println("not creating database again");
        }
        
        // Check to see if we were started with the intent to send a photo
        final Intent queryIntent = getIntent();
        final String queryAction = queryIntent.getAction();
        if (Intent.ACTION_SEND.equals(queryAction))
        {
        	// A picture was retrieved, activate the upload_photo view
        	uploadPhoto(queryIntent);        	
        }
        else {
        	// Launched normally, show the settings menu
        	settings();
        }
    }
    
    public void onStart() {
    	super.onStart();
    	
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();

    }

    /**
     * Implement the Settings view
     */
    private void settings() {

        // Hide the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        // Force a portrait orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        // Show the Settings window
    	setContentView(R.layout.authentication);

    	// Get a context to the status window so it can be updated
        TextView status_text = (TextView) findViewById(R.id.TextView02);
    	
    	/** Determine which action to perform:
    	 * - New user, must begin registration process (Get FROB and send user to website)
    	 * - New user, continue registration process (Use FROB to get auth_token)
    	 * - Existing user, verify authentication (Call getuser() or whatever to get user name)
    	 */
        
        // Call the Flickr helper to determine what mode to be in
        FlickrClient helper = new FlickrClient(m_database);
        
        FlickrClient.AuthorizationState state = null;
        
		try {
			state = helper.getAuthorizationState();
		} catch (Exception e1) {
			// Display an error message to the user
			// TODO: Is this useful?  How to improve it?
			status_text.setText("Error connecting to Flickr service: " + e1.getLocalizedMessage());

			// And print the stack trace, in the off chance someone is debugging the application.
			e1.printStackTrace();
		}
        
        if (state == FlickrClient.AuthorizationState.AUTHORIZED) {
        	// Authorized.  Yaay!
        	String strFormat=getString(R.string.authorize_authorized);
        	String strResult=String.format(strFormat,
        			 					   m_database.getConfigValue("username"),	// TODO: Stringify this
        			                       getString(R.string.app_name));

			status_text.setText(strResult);
			final Button button = (Button) findViewById(R.id.Button01);
			button.setText("Authorize with new account");  // TODO: Stringify this.
        }
        else if (state == FlickrClient.AuthorizationState.NO_LONGER_VALID) {
			status_text.setText(getString(R.string.authorize_not_valid));
        }
        else if (state == FlickrClient.AuthorizationState.WAITING_FOR_AUTHORIZATION) {
			status_text.setText(getString(R.string.authorize_waiting_for_authorization));
        }
        else if (state == FlickrClient.AuthorizationState.NOT_STARTED) {
			status_text.setText(getString(R.string.authorize_not_started)); 
        }
        
        // The joke here is that the 'Authorize Account' button always does the same thing...
		final Button button = (Button) findViewById(R.id.Button01);
		button.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {		            		            	
		        		FlickrClient helper = new FlickrClient(m_database);
		        		String authUrl;
						try {
							authUrl = helper.startAuthorization();
							
			    			System.out.println("Authentication URL: " + authUrl);

			    			// Launch a web browser window to complete the authentication
			        		Uri uri=Uri.parse(authUrl);
			        		startActivity(new Intent(Intent.ACTION_VIEW, uri));
							
						} catch (Exception e) {
							// The 
							// TODO: Is there a more graceful way to handle this?
							new AlertDialog.Builder(v.getContext())
							  .setTitle("title")
							  .setMessage("Oops!  Something went wrong, maybe there is a connection problem?"
									      + e.getLocalizedMessage())
							  .setNeutralButton("Close", new DialogInterface.OnClickListener() {
							     public void onClick(DialogInterface dlg, int sumthin) {
							       // do nothing – it will close on its own
							     }
							  })
							  .show();

							// And print the stack trace, in the off chance someone is debugging the application.
							e.printStackTrace();
						}
		            }
			});
    }
    
    /** 
     * Implement the UploadPhoto view
     */
    public void uploadPhoto(final Intent intent) {
    	
        // Force an orientation based on the system (in case the calling application forced it one way)
    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
    	
    	setContentView(R.layout.upload_photo);
    	
    	// Handle the 'Upload Photo' button
        final Button button = (Button) findViewById(R.id.Button01);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	final String title = ((EditText) findViewById(R.id.TitleText)).getText().toString();
            	final String description = ((EditText) findViewById(R.id.DescriptionText)).getText().toString();
            	final String tags = ((EditText) findViewById(R.id.TagsText)).getText().toString();
            	
            	
            	
            	System.out.println("Intent is: " + intent.getExtras());

            	// TODO: Check that all of these intermediate steps are ok
            	Bundle data = intent.getExtras();
            	Uri uri = (Uri) data.getParcelable(Intent.EXTRA_STREAM);
                
            	if (uri==null) {
            		System.out.println("uri is null!");
            	}
            	else {
            		System.out.println("uri has something in it: " + uri.toString());
            	
            		// Get photo from URI
					try {
						InputStream is = getContentResolver().openInputStream(uri);
							
						// Then send the photo
						FlickrClient helper = new FlickrClient(m_database);
						helper.sendPhoto(title, description, tags, is);
						
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}



					finish();
				}
            }
        });

    }
}