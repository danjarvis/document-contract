/**
 * Cordova Document Contract Plugin
 * <p>
 * (c) Dan Jarvis 2015 :: License MIT
 */
package com.danjarvis.documentcontract;

import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract.*;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

public class DocumentContract extends CordovaPlugin {
    private String INVALID_PARAMS_ERROR = "Invalid parameters specified.";
    private String INVALID_URI_ERROR = "Invalid URI specified.";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        final CallbackContext cb = callbackContext;
        final JSONObject queryArgs;
        if (action.equals("createFile")) {
            queryArgs = args.getJSONObject(0);
            if (null == queryArgs) {
                cb.error(INVALID_PARAMS_ERROR);
                return false;
            }

            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    createFile(queryArgs, cb);
                }
            });
            return true;
        } else if (action.equals("getContract")) {
            queryArgs = args.getJSONObject(0);
            if (null == queryArgs) {
                cb.error(INVALID_PARAMS_ERROR);
                return false;
            }

            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    getContract(queryArgs, cb);
                }
            });
            return true;
        }

        return false;
    }

    /**
     * Creates a new file from the data resolved through the provided content URI.
     *
     * @return name of created file (residing at cordova.file.dataDirectory).
     */
    private void createFile(JSONObject args, CallbackContext callback) {
        try {
            Uri uri;
            String fileName;
            ContentResolver contentResolver;
            InputStream is;
            FileOutputStream fs;
            byte[] buffer;
            int read = 0;

            uri = getUri(args);
            if (null == uri || !(uri.getScheme().equals(ContentResolver.SCHEME_CONTENT))) {
                callback.error(INVALID_URI_ERROR);
                return;
            }

            fileName = getFileName(args);
            if (null == fileName) {
                callback.error(INVALID_PARAMS_ERROR);
                return;
            }

            contentResolver = cordova.getActivity().getContentResolver();
            if (null == contentResolver) {
                callback.error("Failed to get ContentResolver object.");
                return;
            }

            is = contentResolver.openInputStream(uri);
            fs = cordova.getActivity().openFileOutput(fileName, Context.MODE_PRIVATE);

            buffer = new byte[32768];
            while ((read = is.read(buffer, 0, buffer.length)) != -1) {
                fs.write(buffer, 0, read);
            }

            fs.close();
            fs.flush();
            is.close();

            callback.success(fileName);
        } catch (FileNotFoundException fe) {
            callback.error(fe.getMessage());
        } catch (IOException ie) {
            callback.error(ie.getMessage());
        }
    }

    /**
     * Gets the contract details for the provided content URI.
     *
     * @return Contract serialized to a JSONObject
     */
    private void getContract(JSONObject args, CallbackContext callback) {
        try {
            Uri uri;
            Cursor cursor;
            JSONArray results = new JSONArray();

            uri = getUri(args);
            if (null == uri || !(uri.getScheme().equals(ContentResolver.SCHEME_CONTENT))) {
                callback.error(INVALID_URI_ERROR);
                return;
            }
            Search search = getSelectionClause(args);
            cursor = cordova.getActivity().getContentResolver().query(uri, getColumns(args), search!= null ? search.getWhere() : null, search!= null ? search.getArgs() : null, getSortOrder(args));
            if (null != cursor) {
                if (cursor.moveToFirst()) {
                    do {
                        JSONObject tmp = new JSONObject();
                        for (String col : cursor.getColumnNames()) {
                            tmp.put(col, cursor.getString(cursor.getColumnIndex(col)));
                        }
                        results.put(tmp);
                    } while (!isSingle(args) && cursor.moveToNext());
                }
            }
            cursor.close();
            if (results.length() >= 1 && !isSingle(args)) {
                callback.success(results);
            } else if (results.length() == 1 && isSingle(args)) {
                callback.success(results.getJSONObject(0));
            } else {
                if(isSingle(args)){
                    callback.success(new JSONObject());
                }else{
                   callback.success(results);
                }
            }
        } catch (JSONException je) {
            callback.error(je.getMessage());
        }
    }

    private Uri getUri(JSONObject args) {
        try {
            if (!args.has("uri"))
                return null;

            return Uri.parse(args.getString("uri"));
        } catch (JSONException je) {
            return null;
        }
    }

    private String getFileName(JSONObject args) {
        try {
            if (!args.has("fileName"))
                return null;

            return args.getString("fileName");
        } catch (JSONException je) {
            return null;
        }
    }

    private Search getSelectionClause(JSONObject args) {
        try {
            if (!args.has("where")) {
                return null;
            }
            Pattern p = Pattern.compile("'(.*?)'");
            String where = args.getString("where");
            ArrayList<String> argsArray= new ArrayList<>();
            Matcher m = p.matcher(where);
            System.out.println(where);
            while (m.find()) {
                where = where.replaceFirst( m.group(0),"?");
                argsArray.add(m.group(1));
            }
            Search search = new Search();
            search.setArgs(argsArray);
            search.setWhere(where);
            return search;
        } catch (JSONException je) {
            return null;
        }
    }

    private String getSortOrder(JSONObject args) {
        try {
            if (!args.has("sort"))
                return null;
            return args.getString("sort");
        } catch (JSONException je) {
            return null;
        }
    }

    private boolean isSingle(JSONObject args) {
        try {
            if (!args.has("unique"))
                return false;
            return args.getBoolean("unique");
        } catch (JSONException je) {
            return false;
        }
    }

    private String[] getColumns(JSONObject args) {
        try {
            String[] projection;
            JSONArray cols;
            int len;

            if (!args.has("columns"))
                return null;

            cols = args.getJSONArray("columns");
            len = cols.length();
            if (len > 0) {
                projection = new String[len];
                for (int i = 0; i < len; i++)
                    projection[i] = cols.getString(i);
                return projection;
            }

            return null;
        } catch (JSONException je) {
            return null;
        }
    }

    private class Search {
        private String where;
        private ArrayList<String> args;

        public String getWhere() {
            return where;
        }

        public void setWhere(String where) {
            this.where = where;
        }

        public String[] getArgs() {
            return args == null ? null : args.toArray(new String[args.size()]);
        }

        public void setArgs(ArrayList<String> args) {
            this.args = args;
        }
    }
}
