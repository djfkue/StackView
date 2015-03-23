package com.argonmobile.stackview.model;

import android.graphics.Bitmap;

/**
 * Created by argon on 3/22/15.
 */
public class Profile {
    public static class TaskKey {
        public int id;
    }

    public TaskKey key = new TaskKey();
    public Bitmap thumbnail;
    public String activityLabel;

    public boolean isLaunchTarget = true;
}
