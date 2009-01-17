package com.android.fuploadr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Images.Media;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class FUploadr extends Activity {
	UploadrHelper m_database;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        /** Init the database, as all routines will need it. **/
        m_database = new UploadrHelper(this); 
        
        /** Check to see if we were started with the intent to send a photo **/
        final Intent queryIntent = getIntent();
        final String queryAction = queryIntent.getAction();
        if (Intent.ACTION_SEND.equals(queryAction))
        {
        	/** A picture was retrieved, activate the upload_photo view **/
        	uploadPhoto(queryIntent);        	
        }
        else {
        	/** Launched normally, show the settings menu **/
        	settings();
        }
    }
    
    public void onStart() {
    	super.onStart();
    	
    }

    /**
     * Implement the Settings view
     */
    private void settings() {

        /** Hide the title bar **/
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        /** Show the Settings window **/
    	setContentView(R.layout.authentication);

    	/** Get a context to the status window so it can be updated **/
        TextView status_text = (TextView) findViewById(R.id.TextView02);
    	
    	/** Determine which action to perform:
    	 * - New user, must begin registration process (Get FROB and send user to website)
    	 * - New user, continue registration process (Use FROB to get auth_token)
    	 * - Existing user, verify authentication (Call getuser() or whatever to get user name)
    	 */
        
        // Call the flickr helper to determine what mode to be in
        FlickrHelper helper = new FlickrHelper(m_database);
        
        FlickrHelper.AuthorizationState state = helper.getAuthorizationState();
        
        if (state == FlickrHelper.AuthorizationState.AUTHORIZED) {
        	// Authorized.  Yaay!
			status_text.setText("Welcome /username/, you are connected to Flickr.  You " +
					            "can now send photos from any photo application by selecting " +
					            " /fuploader/ in the SENDTO dialog!");
			final Button button = (Button) findViewById(R.id.Button01);
			button.setText("Authorize with new account");  // TODO: Stringify this.
        }
        else if (state == FlickrHelper.AuthorizationState.NO_LONGER_VALID) {
			status_text.setText("Oops!  Your authentication has expired.  Please " +
					            "register again by clicking the 'Authorize Account' " +
					            "button below.");
        }
        else if (state == FlickrHelper.AuthorizationState.WAITING_FOR_AUTHORIZATION) {
			status_text.setText("Sorry, the request did not go through.  Please " +
		            			"try registering again by clicking the 'Authorize Account' " +
            					"button below.");
        }
        else if (state == FlickrHelper.AuthorizationState.NOT_STARTED) {
			status_text.setText("Welcome!  Click the 'Authorize Account' button " +
			          			"to begin.  In the browser window that opens, log " +
			          			"into your account and authorize this app.  Then, " +
			          			"return here to finish the process!  Whew!"); 
        }
        
        // The joke here is that the 'Authorize Account' button always does the same thing...
		final Button button = (Button) findViewById(R.id.Button01);
		button.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {		            		            	
		        		FlickrHelper helper = new FlickrHelper(m_database);
		        		String authUrl;
						try {
							authUrl = helper.startAuthorization();
							
			    			System.out.println("Authentication URL: " + authUrl);

			    			// Launch a web browser window to complete the authentication
			        		Uri uri=Uri.parse(authUrl);
			        		startActivity(new Intent(Intent.ACTION_VIEW, uri));
							
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
		            }
			});
    }
    
    /** 
     * Implement the UploadPhoto view
     */
    public void uploadPhoto(final Intent intent) {
    	setContentView(R.layout.upload_photo);
    	
    	/** Handle the 'Upload Photo' button */
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
						
/**						
						// Grab out the photo and build a post stream out of it
						byte[] imageData = null;

						Bitmap bitmap = Media.getBitmap(getContentResolver(),
								uri);
						ByteArrayOutputStream bytes = new ByteArrayOutputStream();

						bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes);

						imageData = bytes.toByteArray();

						System.out.println("Photo size: " + imageData.length);
**/					
						// Then send the photo
						FlickrHelper helper = new FlickrHelper(m_database);
						helper.sendPhoto(title, description, tags, is);
						
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}



					finish();
				}
            }
        });

    }
}