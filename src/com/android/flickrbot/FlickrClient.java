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

/**
 * Simple client library to access the Flickr API from Android
 * 
 * @author Matt Mets
 */
public class FlickrClient {

	// Note: Don't screw with these.  If you intend to fork this application or
	//       do something else with this code, get your own API key from Flickr.
	//       It's free and painless.
	private static final String KEY = "ae428503428bda12221b5d170edca5a0";
	private static final String SECRET = "b0d05b2be4e7cee4";

	private static final String FROB_NAME = "frob";
	private static final String AUTH_TOKEN_NAME = "auth_token";
	private static final String NSID_NAME = "nsid";
	private static final String USERNAME_NAME = "username";

	private static final String REST_URL = "http://api.flickr.com/services/rest/";
	private static final String UPLOAD_URL = "http://api.flickr.com/services/upload/";
	private static final String AUTH_URL = "http://m.flickr.com/services/auth/";

	/**
	 * Representation of a list of parameters that will be passed to the
	 * Flickr API in a HttpPost request.  Includes a facility to sign the
	 * parameter list while retrieving it. 
	 * TODO: Make this an extension of the multipart object to skip a step.
	 */
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
		public String getSignedList() throws Exception {
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
		public MultipartEntity getMultipartEntity() throws Exception {
			MultipartEntity entity = new MultipartEntity();

		    Iterator<String> iterator = parameters.keySet().iterator();
		    while (iterator.hasNext()) {
		    	Object key = iterator.next();
		    	try {
					entity.addPart(key.toString(), new StringBody(parameters.get(key)));
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }

			entity.addPart("api_sig", new StringBody(getSig()));
		    
			return entity;
		}
		
		/** Get the MD5 signature of the parameter list */ 
		public String getSig() throws Exception {
			/** Build a sorted list of all the parameters, prepended by the
			 *  secret. */
			String list = SECRET;
		    Iterator<String> iterator = parameters.keySet().iterator();
		    while (iterator.hasNext()) {
		    	Object key = iterator.next();
		    	list += key + parameters.get(key);
		    }
			
		    /** Calculate the MD5 hash of the parameter string */
			return MD5(list);
		}
	}

	
	Database m_database;
	
	/**
	 * FlickrHelper constructor
	 * @param database Parameter database to use
	 */
	public FlickrClient(Database database) {
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
	 * @return The current authorization state
	 * @throws Exception Throws an exception if there is a communication error.
	 */
	AuthorizationState getAuthorizationState() throws Exception {
		
    	// First, attempt to retrieve the authentication token.
		String auth_token = m_database.getConfigValue("auth_token");
    	
		// If auth_token has a value, assume that the user has completed
		// registration and return.  
		if (auth_token != null) {
			// TODO: Verify that the authorization is current by polling Flickr.
			return AuthorizationState.AUTHORIZED;
		}
		
		// Otherwise, the user hasn't completed registration.  Determine if
		// there is an ongoing registration that should be continued.
		
		// A registration is in progress if a frob exists in the database.
		String frob = m_database.getConfigValue("frob");
		if (frob == null) {
			return AuthorizationState.NOT_STARTED;
		}

		// Determine if the registration in progress has been completed by
		// the user.  Note: AuthToken will throw if communication fails with
		// Flickr for any reason.
		if ( getAuthToken() ) {
			return AuthorizationState.AUTHORIZED;
		}
		else {
			return AuthorizationState.WAITING_FOR_AUTHORIZATION;
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
	public void sendPhoto(String title, String description, String tags, InputStream image) throws Exception {
		// TODO: Verify that we are authorized first.

		// Build a representation of the parameter list, and use it to sign the request.
		parameterList params = this.new parameterList();
		params.addParameter("api_key", KEY);
		params.addParameter("auth_token", m_database.getConfigValue(AUTH_TOKEN_NAME));
		
		// Add all of the extra parameters that are passed along with the image.
		if (title.length() > 0) {
			params.addParameter("title", title);
		}
		
		if (description.length() > 0) {
			params.addParameter("description", description);
		}

		if (tags.length() > 0) {
			params.addParameter("tags", tags);
		}

		// Build a multipart HTTP request to post to Flickr
		
		// Add the text parameters
		MultipartEntity multipart = params.getMultipartEntity();

		// then the photo data
		multipart.addPart("photo", new InputStreamBody(image, "photo"));
    	
		// Build the rest of the players
		HttpClient client = new DefaultHttpClient();		// HttpClient makes requests
		HttpPost postrequest = new HttpPost(UPLOAD_URL);	// HttpPost is the type of request we are making
		postrequest.setEntity(multipart);
		HttpResponse response;								// HttpResponse holds the return data from HttpPost 

		

		// TODO: Is this necessary?
		if (postrequest == null) {
			// Building the post request has failed
			throw new Exception ("postrequest could not be built");
		}
		
		// Post the photo.  This will throw an exception if the connection fails.
		response = client.execute(postrequest);

		System.out.println("POST response code: "
				+ response.getStatusLine().getReasonPhrase());
		HttpEntity resp = response.getEntity();
		
		
		if (resp == null) {
			throw new Exception("Response entity is empty.");
		}
		
		byte[] b = new byte[999];
		resp.getContent().read(b);
		System.out.println("Response: " + new String(b));
		
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
		
		HttpClient client = new DefaultHttpClient();
		HttpGet getrequest = new HttpGet(REST_URL + params.getSignedList());
		
		// Send GET request
		HttpResponse response = client.execute(getrequest);
		int statusCode = response.getStatusLine().getStatusCode();
				
		if (statusCode != HttpStatus.SC_OK) {
			throw new Exception("Method failed: " + response.getStatusLine());
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
			
			throw new Exception("Flickr request failed with error code: " + code + ", " + msg);
		}
		
		/**
		 * Create a Flickr login link
		 * http://www.flickr.com/services/auth/?api_key=[api_key]&perms=[perms]&frob=[frob]&api_sig=[api_sig] 
		 */
		params.clear();
		params.addParameter("api_key", KEY);
		params.addParameter("frob", frob);
		params.addParameter("perms", "write");
        
        return AUTH_URL + params.getSignedList();
	}
    
	boolean getAuthToken() throws Exception {
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
			throw new Exception("Method failed: " + response.getStatusLine());
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
		
		if (tokenNode == null) {
			NodeList error = parsedResponse.getElementsByTagName("err");
			// Get Flickr error code and msg
			// TODO: Define this better
			String code = error.item(0).getAttributes().item(0).getNodeValue();
			String msg = error.item(0).getAttributes().item(1).getNodeValue();
			System.out.println("Flickr request failed with error code " + code + ", " + msg);
			
			return false;
		}			
			
		token = tokenNode.getFirstChild().getNodeValue();
		System.out.println("Successfully retrieved token: " + token);		
		
		
		// TODO: Save other stuff such as the user name, etc?
        m_database.deleteConfigValue(FROB_NAME);
		m_database.setConfigValue(AUTH_TOKEN_NAME, token);

		// TODO: Check that the perms field is "write"
		
		// Assume that all of this will work.  If they don't, we throw.
		tokenResponse = parsedResponse.getElementsByTagName("user");
		String nsid = tokenResponse.item(0).getAttributes().getNamedItem("nsid").getNodeValue();
		String username = tokenResponse.item(0).getAttributes().getNamedItem("username").getNodeValue();
		
		m_database.setConfigValue(NSID_NAME, nsid);
		m_database.setConfigValue(USERNAME_NAME, username);		
		
		return true;
	}
	
	/**
	 * Get the MD5 hash of a text string
	 */
	public static String MD5(String text) throws Exception
	{
		String md5Text = "";
		
		MessageDigest digest = MessageDigest.getInstance("MD5");
		md5Text = new BigInteger(1, digest.digest((text).getBytes())).toString(16);
		
        if (md5Text.length() == 31) {
            md5Text = "0" + md5Text;
        }
		return md5Text;
	}
}