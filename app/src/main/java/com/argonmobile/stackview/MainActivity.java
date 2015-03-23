package com.argonmobile.stackview;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import com.argonmobile.stackview.model.Profile;
import com.argonmobile.stackview.model.ProfileStack;
import com.argonmobile.stackview.util.ReferenceCountedTrigger;
import com.argonmobile.stackview.view.RecentsView;
import com.argonmobile.stackview.view.ViewAnimation;

import java.util.ArrayList;


public class MainActivity extends ActionBarActivity {

    private RecentsConfiguration mConfig;
    private RecentsView mRecentsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Initialize the loader and the configuration
        mConfig = RecentsConfiguration.reinitialize(this);

        setContentView(R.layout.activity_main);

        mRecentsView = (RecentsView) findViewById(R.id.recents_view);
        mRecentsView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        updateRecentsTasks();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Register any broadcast receivers for the task loader
        //RecentProfileLoader.getInstance().registerReceivers(this, mRecentsView);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Mark Recents as visible

        ReferenceCountedTrigger t = new ReferenceCountedTrigger(this, null, null, null);
        mRecentsView.startEnterRecentsAnimation(new ViewAnimation.TaskViewEnterContext(t));
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Remove all the views
        mRecentsView.removeAllTaskStacks();


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void updateRecentsTasks() {

        // Load all the tasks

        ArrayList<ProfileStack> stacks = new ArrayList<>();
        ProfileStack mockStack = new ProfileStack();
        Profile mockProfile1 = new Profile();
        mockProfile1.isLaunchTarget = true;
        mockProfile1.key = new Profile.TaskKey();
        mockProfile1.key.id = 0;
        mockStack.addTask(mockProfile1);

        Profile mockProfile2 = new Profile();
        mockProfile2.isLaunchTarget = true;
        mockProfile2.key = new Profile.TaskKey();
        mockProfile2.key.id = 1;
        mockStack.addTask(mockProfile2);

        mockProfile2 = new Profile();
        mockProfile2.isLaunchTarget = true;
        mockProfile2.key = new Profile.TaskKey();
        mockProfile2.key.id = 2;
        mockStack.addTask(mockProfile2);

        mockProfile2 = new Profile();
        mockProfile2.isLaunchTarget = true;
        mockProfile2.key = new Profile.TaskKey();
        mockProfile2.key.id = 3;
        mockStack.addTask(mockProfile2);

        mockProfile2 = new Profile();
        mockProfile2.isLaunchTarget = true;
        mockProfile2.key = new Profile.TaskKey();
        mockProfile2.key.id = 4;
        mockStack.addTask(mockProfile2);

        mockProfile2 = new Profile();
        mockProfile2.isLaunchTarget = true;
        mockProfile2.key = new Profile.TaskKey();
        mockProfile2.key.id = 5;
        mockStack.addTask(mockProfile2);

        mockProfile2 = new Profile();
        mockProfile2.isLaunchTarget = true;
        mockProfile2.key = new Profile.TaskKey();
        mockProfile2.key.id = 6;
        mockStack.addTask(mockProfile2);

        mockProfile2 = new Profile();
        mockProfile2.isLaunchTarget = true;
        mockProfile2.key = new Profile.TaskKey();
        mockProfile2.key.id = 7;
        mockStack.addTask(mockProfile2);

        mockProfile2 = new Profile();
        mockProfile2.isLaunchTarget = true;
        mockProfile2.key = new Profile.TaskKey();
        mockProfile2.key.id = 8;
        mockStack.addTask(mockProfile2);

        mockProfile2 = new Profile();
        mockProfile2.isLaunchTarget = true;
        mockProfile2.key = new Profile.TaskKey();
        mockProfile2.key.id = 9;
        mockStack.addTask(mockProfile2);

        stacks.add(mockStack);
        if (!stacks.isEmpty()) {
            mRecentsView.setTaskStacks(stacks);
        }
        mConfig.launchedWithNoRecentTasks = false;

        // Mark the task that is the launch target
        int taskStackCount = stacks.size();
        if (mConfig.launchedToTaskId != -1) {
            for (int i = 0; i < taskStackCount; i++) {
                ProfileStack stack = stacks.get(i);
                ArrayList<Profile> tasks = stack.getTasks();
                int taskCount = tasks.size();
                for (int j = 0; j < taskCount; j++) {
                    Profile t = tasks.get(j);
                    if (t.key.id == mConfig.launchedToTaskId) {
                        t.isLaunchTarget = true;
                        break;
                    }
                }
            }
        }
    }
}
