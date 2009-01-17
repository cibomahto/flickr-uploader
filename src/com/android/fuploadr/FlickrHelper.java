package com.android.fuploadr;

/**
 * Flickr Web Service API Example: Authenticate Desktop Application User
 *
 * @author Daniel Jones www.danieljones.org
 * Copyright 2007
 * 
 * This example shows how to perform the steps necessary to authenticate a 
 * Yahoo! Flickr user. Once the user has been authenticated, you have full
 * access to the Flickr API.
 *  
 * The Flickr Authentication API Desktop Applications How-To can be found 
 * here: http://www.flickr.com/services/api/auth.howto.desktop.html
 *
 * Before you can use the Flickr API, you must obtain an API key at the following 
 * URL: http://www.flickr.com/services/api/keys/apply/
 * 
 * For the purposes of this example, be sure to choose the Desktop Application
 * radio button for the Authentication Type. We do not want the user
 * automatically redirected to a callback URL with the frob passed in the URL
 * as a GET argument because our desktop application's token request will fail.
 * You should choose Web Application for the Authentication Type if you want
 * the frob argument automatically passed back to the auth handler callback URL
 * for a subsequent call to flickr.auth.getToken.
 * 
 * Once you are comfortable with user authentication, continue with the FlickrRotate
 * example to see a live example of the Flickr API.
 * 
 */


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore.Images.Media;

public class FlickrHelper {
/*
	private static final String KEY = "";
	private static final String SECRET = "";
	private static final String TOKEN = "";
*/

	// Note: Don't screw with these.  If you intend to fork this application or
	//       do something else with this code, get your own API key from Flickr.
	//       It's free and painless.
	private static final String KEY = "ae428503428bda12221b5d170edca5a0";
	private static final String SECRET = "b0d05b2be4e7cee4";

	private static final String FROB_NAME = "frob";
	private static final String AUTH_TOKEN_NAME = "auth_token";

	String REST_URL = "http://api.flickr.com/services/rest/";
	String UPLOAD_URL = "http://api.flickr.com/services/upload/";

	public class parameterList {
		private TreeMap<String, String> parameters = new TreeMap<String, String>();
		
		public void addParameter(String name, String value) {
			parameters.put(name, value);
		}
		
		public void clear() {
			parameters.clear();
		}
		
		/**
		 * Get a string representation of the parameter list, suitable for a
		 * GET query.  Note that this automatically signs the parameter list.
		 * */
		public String getSignedList() {
		    // Add each of the parameters in the form ?keya=valuea&keyb=valueb
			String list = "?";
			
		    Iterator iterator = parameters.keySet().iterator();
		    boolean firstItem = true;
		    while (iterator.hasNext()) {
		    	if(firstItem) {
		    		firstItem = false;
		    	}
		    	else {
		    		list += "&";
		    	}
		    	
		    	Object key = iterator.next();
		    	list += key + "=" + parameters.get(key);
		    }
		    
		    list += "&api_sig=" + getSig();
			
			return list;
		}
		
		/**
		 * Get a MultipartEntity object representation of the parameter list,
		 * suitable for passing to a httpClient() execute.
		 */
		public MultipartEntity getMultipartEntity() {
			MultipartEntity entity = new MultipartEntity();

		    Iterator iterator = parameters.keySet().iterator();
		    while (iterator.hasNext()) {
		    	Object key = iterator.next();
		    	try {
					entity.addPart(key.toString(), new StringBody(parameters.get(key)));
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }

	    	try {
				entity.addPart("api_sig", new StringBody(getSig()));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		    
			return entity;
		}
		
		/** Get the MD5 signature of the parameter list */ 
		public String getSig() {
			/** Build a sorted list of all the parameters, prepended by the
			 *  secret. */
			String list = SECRET;
		    Iterator iterator = parameters.keySet().iterator();
		    while (iterator.hasNext()) {
		    	Object key = iterator.next();
		    	list += key + parameters.get(key);
		    }
			
		    /** Calculate the MD5 hash of the parameter string */
			return MD5(list);
		}
	}


	
	UploadrHelper m_database;
	
	/**
	 * FlickrHelper constructor
	 * @param database Parameter database to use
	 */
	public FlickrHelper(UploadrHelper database) {
		m_database = database;	
	}
	
	public enum AuthorizationState {
		NOT_STARTED,
		WAITING_FOR_AUTHORIZATION,
		AUTHORIZED,
		NO_LONGER_VALID
	}
	
	/**
	 * Determine what stage of the authorization state that the user is in.
	 * @return The last known authorization state
	 */
	AuthorizationState getAuthorizationState() {
    	// First, attempt to retrieve the authentication token.
		String auth_token = m_database.getConfigValue("auth_token");
    	
		// If there is no auth_token, the user hasn't completed registration.
		if (auth_token == null) {
			
			String frob = m_database.getConfigValue("frob");
			if (frob == null) {
				return AuthorizationState.NOT_STARTED;
			}
			else {
				// Check if the user has validated the FROB
				try {
					getAuthToken();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				// Check again
				// TODO: use a return code on getAuthToken instead of this.
				auth_token = m_database.getConfigValue("auth_token");
				
				if (auth_token == null) {
					return AuthorizationState.WAITING_FOR_AUTHORIZATION;
				}
				else {
					return AuthorizationState.AUTHORIZED;					
				}
			}
		}
		else {
			// TODO: Verify that the authorization is current by polling Flickr.
			return AuthorizationState.AUTHORIZED;
		}
	}	
	
	/**
	 * Upload a photo to Flickr using the current user credentials.
	 * 
	 * @param title Photo title
	 * @param description Photo description
	 * @param tags Tags to assign to photo
	 * @param image JPEG object representing the photo
	 */
	public void sendPhoto(String title, String description, String tags, InputStream image) {
		// TODO: Verify that we are authorized first.

		/** Build a representation of the parameter list, and use it to sign the request. **/
		parameterList params = this.new parameterList();
		params.addParameter("api_key", KEY);
		params.addParameter("auth_token", m_database.getConfigValue(AUTH_TOKEN_NAME));
		
		/** Add all of the extra parameters that are passed along with the image **/
		if (title.length() > 0) {
			params.addParameter("title", title);
		}
		
		if (description.length() > 0) {
			params.addParameter("description", description);
		}

		if (tags.length() > 0) {
			params.addParameter("tags", tags);
		}
		
		System.out.println( params.getSignedList() );

		// Get a multipart representation of the parameter list, so that the picture can be appended to it.
		MultipartEntity multipart = params.getMultipartEntity();

//		System.out.println( "image size is: " + imageData.length);
//		InputStream ins = new ByteArrayInputStream(imageData);
		multipart.addPart("photo", new InputStreamBody(image, "photo"));
    	
		HttpClient client = new DefaultHttpClient();

		HttpPost postrequest = new HttpPost(UPLOAD_URL);
		postrequest.setEntity(multipart);
		
		HttpResponse response;
		
		if (postrequest == null) {
			System.out.println( "post request is somehow null!");
		}
		else {
			System.out.println( "post request seems good.");			
		}
		
		try {
			response = client.execute(postrequest);

			System.out.println( "POST response code: " + response.getStatusLine().getReasonPhrase());
			HttpEntity resp = response.getEntity();
			
			if( resp == null) {
				System.out.println( "entity is null.");
			}
			else {
				byte[] b = new byte[999];
				resp.getContent().read(b);
				
				System.out.println( "Respnse: " + new String(b));				
			}
			
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

		
	}


	/**
	 * Retrieves a URL that the user should browse to in order to authorize
	 * their account with the program.  This stores a value in the class
	 * variable frob, which is used in getAuthToken() later to complete the
	 * authentication.
	 * 
	 * @return URL that the program should redirect the user to, in order to complete authorization. 
	 */
	public String startAuthorization() throws Exception {
        m_database.deleteConfigValue(FROB_NAME);
		m_database.deleteConfigValue(AUTH_TOKEN_NAME);

		
		/**
		 * Request a frob to identify the login session. This call requires 
		 * a signature. The signature starts with your shared secret and
		 * is followed by your API key and the method name. The API key and
		 * method name are prepended by the words "api_key" and "method" as
		 * shown in the following line.
		**/
		String methodGetFrob = "flickr.auth.getFrob";
		
		parameterList params = this.new parameterList();
		params.addParameter("api_key", KEY);
		params.addParameter("method", methodGetFrob);
//		System.out.println("GET frob request: " + REST_URL + params.getSignedList());		
		
		HttpClient client = new DefaultHttpClient();
		HttpGet getrequest = new HttpGet(REST_URL + params.getSignedList());
		
		// Send GET request
		HttpResponse response = client.execute(getrequest);
		int statusCode = response.getStatusLine().getStatusCode();
				
		if (statusCode != HttpStatus.SC_OK) {
			System.err.println("Method failed: " + response.getStatusLine());
		}
		InputStream rstream = null;
		
		// Get the response body
		rstream = response.getEntity().getContent();
		
		/**
		 * Retrieve the XML response to the frob request and get the frob value.
		 */
		Document parsedResponse = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rstream);
		
		String frob;
		
		// Check if frob is in the response
		NodeList frobResponse = parsedResponse.getElementsByTagName("frob");
		Node frobNode = frobResponse.item(0);
		if (frobNode != null) {
			frob = frobNode.getFirstChild().getNodeValue();
			System.out.println("Successfully retrieved frob: " + frob);
			m_database.setConfigValue(FROB_NAME, frob);
		} else {
			// Get Flickr error code and msg
			NodeList error = parsedResponse.getElementsByTagName("err");
			String code = error.item(0).getAttributes().item(0).getNodeValue();
			String msg = error.item(0).getAttributes().item(1).getNodeValue();
			System.out.println("Flickr request failed with error code " + code + ", " + msg);
			return null;
		}
		
		/**
		 * Create a Flickr login link
		 * http://www.flickr.com/services/auth/?api_key=[api_key]&perms=[perms]&frob=[frob]&api_sig=[api_sig] 
		 */
		params.clear();
		params.addParameter("api_key", KEY);
		params.addParameter("frob", frob);
		params.addParameter("perms", "write");
        
        return "http://m.flickr.com/services/auth/" + params.getSignedList();
	}
    
	void getAuthToken() throws Exception {
		// TODO: Check the state before running this.
		
		/** Retrieve the frob from the database **/
		String frob = m_database.getConfigValue(FROB_NAME);
		
		/**
		 * Get auth token using frob. Once again, a signature is required
		 * for authenticated calls to the Flickr API.  
		 */
		parameterList params = this.new parameterList();
		params.addParameter("api_key", KEY);
		params.addParameter("frob", frob);
		params.addParameter("method", "flickr.auth.getToken");        

		HttpClient client = new DefaultHttpClient();
		HttpGet getrequest = new HttpGet(REST_URL + params.getSignedList());
			
		// Send GET request
		HttpResponse response = client.execute(getrequest);
		int statusCode = response.getStatusLine().getStatusCode();
		
		if (statusCode != HttpStatus.SC_OK) {
			System.err.println("Method failed: " + response.getStatusLine());
		}
		
		InputStream rstream = null;
		
		// Get the response body
		rstream = response.getEntity().getContent();
		/**
		 * Retrieve the XML response to the token request and get the token value
		 */
		Document parsedResponse = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rstream);
		
		String token = null;
		
		// Check if token is in the response
		NodeList tokenResponse = parsedResponse.getElementsByTagName("token");
		Node tokenNode = tokenResponse.item(0);
		if (tokenNode != null) {
			token = tokenNode.getFirstChild().getNodeValue();
			System.out.println("Successfully retrieved token: " + token);
		} else {
			NodeList error = parsedResponse.getElementsByTagName("err");
			// Get Flickr error code and msg
			String code = error.item(0).getAttributes().item(0).getNodeValue();
			String msg = error.item(0).getAttributes().item(1).getNodeValue();
			System.out.println("Flickr request failed with error code " + code + ", " + msg);
			return;
		}
		
		// TODO: Save other stuff such as the user name, etc?
        m_database.deleteConfigValue(FROB_NAME);
		m_database.setConfigValue(AUTH_TOKEN_NAME, token);
	}
	
	/**
	 * Get the MD5 hash of a text string
	 */
	public static String MD5(String text)
	{
		String md5Text = "";
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			md5Text = new BigInteger(1, digest.digest((text).getBytes())).toString(16);
		} catch (Exception e) {
			System.out.println("Error in call to MD5");
		}
		
        if (md5Text.length() == 31) {
            md5Text = "0" + md5Text;
        }
		return md5Text;
	}
}