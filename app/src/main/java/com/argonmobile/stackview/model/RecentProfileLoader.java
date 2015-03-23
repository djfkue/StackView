package com.argonmobile.stackview.model;

import android.content.Context;

/**
 * Created by argon on 3/23/15.
 */
public class RecentProfileLoader {

    static RecentProfileLoader sInstance;

    /** Private Constructor */
    private RecentProfileLoader(Context context) {

    }

    /** Initializes the recents task loader */
    public static RecentProfileLoader initialize(Context context) {
        if (sInstance == null) {
            sInstance = new RecentProfileLoader(context);
        }
        return sInstance;
    }

    /** Returns the current recents task loader */
    public static RecentProfileLoader getInstance() {
        return sInstance;
    }
}
