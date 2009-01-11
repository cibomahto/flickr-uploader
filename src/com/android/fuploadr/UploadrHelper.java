package com.android.fuploadr;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * This class manages access to the configuration settings, enabling them to
 * persistent across sessions.
 */
public class UploadrHelper {

    private static final String DATABASE_NAME = "uploadr.db";
    private static final int DATABASE_VERSION = 1;
    
    private static final String CONFIG_TABLE_NAME = "config";
    
    private static final String CONFIG_NAME = "option_name";
    private static final String CONFIG_VALUE = "option_value";
    private static final String CONFIG_ID = "_id";

    private SQLiteDatabase  m_db; 
    
    /**
     * At construction, use the database helper to retrieve the database.
     * @param context Application context, passed to DatabaseHelper() context.
     */
    UploadrHelper(Context context) {
    	m_db=(new DatabaseHelper(context).getWritableDatabase());
    	
    	if (m_db == null) {
    		// TODO: How to throw an exception from the constructor?
    	}
    }
    
    /**
     * Search for a config parameter.
     * @param name Name of configuration parameter to retrieve.
     * @return Value of configuration parameter, if present.  Null if not.
     */
	String getConfigValue(String name) {
		String[] columns={CONFIG_VALUE};
		String[] parms={name};
		Cursor result=m_db.query(CONFIG_TABLE_NAME, columns, CONFIG_NAME + "=?",
		                       parms, null, null, null);
		
		if (result.getCount() == 0) {
			return null;
		}
		// TODO: Handle errors: Did the search result in anything?
		//       Is there a string in it?
		result.moveToFirst();
		return result.getString(0);
	}
    
	/**
	 * Set a config parameter.
	 * @param name Name of configuration parameter to set.
	 * @param value Value to assign to parameter.
	 */
	void setConfigValue(String name, String value) {
		ContentValues cv=new ContentValues();
		cv.put(CONFIG_VALUE, value);
		
		String[] names=new String[] {name};
		
		System.out.println("writing: " + name + " " + value);
		
		Integer n = m_db.update(CONFIG_TABLE_NAME, cv, CONFIG_NAME + "=?", names);

		/** If the config value could not be found, add it. **/
		// TODO: Can this all be done in one step? :-P
		if (n == 0) {
			cv.put(CONFIG_NAME, name);
			m_db.insert(CONFIG_TABLE_NAME, null, cv);
		}
	}

	/**
	 * Delete a config parameter.
	 * @param name Name of configuration parameter to delete.
	 */
	void deleteConfigValue(String name) {
		String[] names=new String[] {name};
		
		m_db.delete(CONFIG_TABLE_NAME, CONFIG_NAME + "=?", names);
	}
	
	
    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + CONFIG_TABLE_NAME + " ("
                    + CONFIG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + CONFIG_NAME + " TEXT,"
                    + CONFIG_VALUE + " TEXT"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }

}
