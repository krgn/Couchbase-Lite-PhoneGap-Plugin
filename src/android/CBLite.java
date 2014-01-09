package com.couchbase.cblite.phonegap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.couchbase.lite.Database;
import com.couchbase.lite.Manager;
import com.couchbase.lite.ManagerOptions;
import com.couchbase.lite.listener.LiteListener;
import com.couchbase.lite.router.URLStreamHandlerFactory;
import com.couchbase.lite.View;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.javascript.JavaScriptViewCompiler;

import java.io.File;
import java.io.IOException;

public class CBLite extends CordovaPlugin {

    private CallbackContext callbackContext;
	private static final int DEFAULT_LISTEN_PORT = 5984;
	private boolean initFailed = false;
	private int listenPort;

	/**
	 * Constructor.
	 */
	public CBLite() {
		super();
		System.out.println("CBLite() constructor called");
	}

	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		System.out.println("initialize() called");

		super.initialize(cordova, webView);
		initCBLite();
	}

	private void initCBLite() {
		try {
			URLStreamHandlerFactory.registerSelfIgnoreError();

			View.setCompiler(new JavaScriptViewCompiler());

			File filesDir = this.cordova.getActivity().getFilesDir();

            Manager manager = startLite(filesDir);
            manager.setDefaultReplicationChangeListener(new ReplicationObserver());

			listenPort = startListener(DEFAULT_LISTEN_PORT, manager);

			System.out.println("initCBLite() completed successfully");
		} catch (final Exception e) {
			e.printStackTrace();
			initFailed = true;
		}
	}

	@Override
	public boolean execute(String action, JSONArray args,
			CallbackContext callback) {
		if (action.equals("getURL")) {
			try {
				if (initFailed == true) {
					callback.error("Failed to initialize couchbase lite.  See console logs");
					return false;
				} else {
					String callbackRespone = String.format(
							"http://localhost:%d/", listenPort);

					callback.success(callbackRespone);

					return true;
				}
			} catch (final Exception e) {
				e.printStackTrace();
				callback.error(e.getMessage());
			}
		}

        else if(action.equals("subscribeEvents")) {
            if (this.callbackContext != null) {
                callbackContext.error("Event listener already running.");
                return true;
            }
            this.callbackContext = callback;

            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);

            return true;
        }
		return false;
	}

	protected Manager startLite(File dirAbsolutePath) {
        Manager manager;
		try {
			manager = new Manager(dirAbsolutePath, Manager.DEFAULT_OPTIONS);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return manager;
	}

	private int startListener(int listenPort, Manager manager) {
		LiteListener listener = new LiteListener(manager, listenPort);
		int boundPort = listener.getListenPort();
		Thread thread = new Thread(listener);
		thread.start();
		return boundPort;
	}

	public void onResume(boolean multitasking) {
		System.out.println("CBLite.onResume() called");
	}

	public void onPause(boolean multitasking) {
		System.out.println("CBLite.onPause() called");
	}

    public void triggerEvent(JSONObject info, boolean keepCallback) {
        if(callbackContext != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, info);
            pluginResult.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(pluginResult);
        }
    }

    class ReplicationObserver implements Replication.ChangeListener {
        ReplicationObserver() {}

        @Override
        public void changed(Replication.ChangeEvent event) {
            if(callbackContext != null) {
                Replication replicator = event.getSource();
                JSONObject jsonEvent = new JSONObject();

                try {
                    jsonEvent.put("_id", replicator.getSessionID());
                    jsonEvent.put("name", replicator.isRunning() ? "CBL_ReplicatorProgressChanged" : "CBL_ReplicatorStopped");
                    jsonEvent.put("changesProcessed", replicator.getCompletedChangesCount());
                    jsonEvent.put("changesTotal", replicator.getChangesCount());
                    jsonEvent.put("running", replicator.isRunning());
                    jsonEvent.put("active", replicator.isActive());
                }
                catch(JSONException e) {
                    e.printStackTrace();
                }
                triggerEvent(jsonEvent, true);
            }
        }
    }
}
