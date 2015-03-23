package com.argonmobile.stackview.model;


import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;


/**
 * An interface for a task filter to query whether a particular task should show in a stack.
 */
interface TaskFilter {
    /** Returns whether the filter accepts the specified task */
    public boolean acceptTask(Profile t, int index);
}

/**
 * A list of filtered tasks.
 */
class FilteredTaskList {
    ArrayList<Profile> mTasks = new ArrayList<Profile>();
    ArrayList<Profile> mFilteredTasks = new ArrayList<Profile>();
    HashMap<Profile.TaskKey, Integer> mTaskIndices = new HashMap<Profile.TaskKey, Integer>();
    TaskFilter mFilter;

    /** Sets the task filter, saving the current touch state */
    boolean setFilter(TaskFilter filter) {
        ArrayList<Profile> prevFilteredTasks = new ArrayList<Profile>(mFilteredTasks);
        mFilter = filter;
        updateFilteredTasks();
        if (!prevFilteredTasks.equals(mFilteredTasks)) {
            return true;
        } else {
            // If the tasks are exactly the same pre/post filter, then just reset it
            mFilter = null;
            return false;
        }
    }

    /** Removes the task filter and returns the previous touch state */
    void removeFilter() {
        mFilter = null;
        updateFilteredTasks();
    }

    /** Adds a new task to the task list */
    void add(Profile t) {
        mTasks.add(t);
        updateFilteredTasks();
    }

    /** Sets the list of tasks */
    void set(List<Profile> tasks) {
        mTasks.clear();
        mTasks.addAll(tasks);
        updateFilteredTasks();
    }

    /** Removes a task from the base list only if it is in the filtered list */
    boolean remove(Profile t) {
        if (mFilteredTasks.contains(t)) {
            boolean removed = mTasks.remove(t);
            updateFilteredTasks();
            return removed;
        }
        return false;
    }

    /** Returns the index of this task in the list of filtered tasks */
    int indexOf(Profile t) {
        if (mTaskIndices.containsKey(t.key)) {
            return mTaskIndices.get(t.key);
        }
        return -1;
    }

    /** Returns the size of the list of filtered tasks */
    int size() {
        return mFilteredTasks.size();
    }

    /** Returns whether the filtered list contains this task */
    boolean contains(Profile t) {
        return mTaskIndices.containsKey(t.key);
    }

    /** Updates the list of filtered tasks whenever the base task list changes */
    private void updateFilteredTasks() {
        mFilteredTasks.clear();
        if (mFilter != null) {
            int taskCount = mTasks.size();
            for (int i = 0; i < taskCount; i++) {
                Profile t = mTasks.get(i);
                if (mFilter.acceptTask(t, i)) {
                    mFilteredTasks.add(t);
                }
            }
        } else {
            mFilteredTasks.addAll(mTasks);
        }
        updateFilteredTaskIndices();
    }

    /** Updates the mapping of tasks to indices. */
    private void updateFilteredTaskIndices() {
        mTaskIndices.clear();
        int taskCount = mFilteredTasks.size();
        for (int i = 0; i < taskCount; i++) {
            Profile t = mFilteredTasks.get(i);
            mTaskIndices.put(t.key, i);
        }
    }

    /** Returns whether this task list is filtered */
    boolean hasFilter() {
        return (mFilter != null);
    }

    /** Returns the list of filtered tasks */
    ArrayList<Profile> getTasks() {
        return mFilteredTasks;
    }
}

/**
 * The task stack contains a list of multiple tasks.
 */
public class ProfileStack {

    /** Task stack callbacks */
    public interface TaskStackCallbacks {
        /* Notifies when a task has been added to the stack */
        public void onStackTaskAdded(ProfileStack stack, Profile t);
        /* Notifies when a task has been removed from the stack */
        public void onStackTaskRemoved(ProfileStack stack, Profile removedTask, Profile newFrontMostTask);
        /** Notifies when the stack was filtered */
        public void onStackFiltered(ProfileStack newStack, ArrayList<Profile> curTasks, Profile t);
        /** Notifies when the stack was un-filtered */
        public void onStackUnfiltered(ProfileStack newStack, ArrayList<Profile> curTasks);
    }

    /** A pair of indices representing the group and task positions in the stack and group. */
    public static class GroupTaskIndex {
        public int groupIndex; // Index in the stack
        public int taskIndex;  // Index in the group

        public GroupTaskIndex() {}

        public GroupTaskIndex(int gi, int ti) {
            groupIndex = gi;
            taskIndex = ti;
        }
    }

    // The task offset to apply to a task id as a group affiliation
    static final int IndividualTaskIdOffset = 1 << 16;

    FilteredTaskList mTaskList = new FilteredTaskList();
    TaskStackCallbacks mCb;

    /** Sets the callbacks for this task stack */
    public void setCallbacks(TaskStackCallbacks cb) {
        mCb = cb;
    }

    /** Adds a new task */
    public void addTask(Profile t) {
        mTaskList.add(t);
        if (mCb != null) {
            mCb.onStackTaskAdded(this, t);
        }
    }

    /** Removes a task */
    public void removeTask(Profile t) {
        if (mTaskList.contains(t)) {
            // Remove the task from the list
            mTaskList.remove(t);

            Profile newFrontMostTask = getFrontMostTask();
            if (mCb != null) {
                // Notify that a task has been removed
                mCb.onStackTaskRemoved(this, t, newFrontMostTask);
            }
        }
    }

    /** Sets a few tasks in one go */
    public void setTasks(List<Profile> tasks) {
        ArrayList<Profile> taskList = mTaskList.getTasks();
        int taskCount = taskList.size();
        for (int i = 0; i < taskCount; i++) {
            Profile t = taskList.get(i);
            // Remove the task from the list
            mTaskList.remove(t);

            if (mCb != null) {
                // Notify that a task has been removed
                mCb.onStackTaskRemoved(this, t, null);
            }
        }
        mTaskList.set(tasks);
        for (Profile t : tasks) {
            if (mCb != null) {
                mCb.onStackTaskAdded(this, t);
            }
        }
    }

    /** Gets the front task */
    public Profile getFrontMostTask() {
        if (mTaskList.size() == 0) return null;
        return mTaskList.getTasks().get(mTaskList.size() - 1);
    }

    /** Gets the tasks */
    public ArrayList<Profile> getTasks() {
        return mTaskList.getTasks();
    }

    /** Gets the number of tasks */
    public int getTaskCount() {
        return mTaskList.size();
    }

    /** Returns the index of this task in this current task stack */
    public int indexOfTask(Profile t) {
        return mTaskList.indexOf(t);
    }

    /** Finds the task with the specified task id. */
    public Profile findTaskWithId(int taskId) {
        ArrayList<Profile> tasks = mTaskList.getTasks();
        int taskCount = tasks.size();
        for (int i = 0; i < taskCount; i++) {
            Profile task = tasks.get(i);
            if (task.key.id == taskId) {
                return task;
            }
        }
        return null;
    }

    /******** Filtering ********/

    /** Filters the stack into tasks similar to the one specified */
    public void filterTasks(final Profile t) {
        ArrayList<Profile> oldStack = new ArrayList<Profile>(mTaskList.getTasks());

        // Set the task list filter
        boolean filtered = mTaskList.setFilter(new TaskFilter() {
            @Override
            public boolean acceptTask(Profile at, int i) {
                return t.key.id ==
                        at.key.id;
            }
        });
        if (filtered && mCb != null) {
            mCb.onStackFiltered(this, oldStack, t);
        }
    }

    /** Unfilters the current stack */
    public void unfilterTasks() {
        ArrayList<Profile> oldStack = new ArrayList<Profile>(mTaskList.getTasks());

        // Unset the filter, then update the virtual scroll
        mTaskList.removeFilter();
        if (mCb != null) {
            mCb.onStackUnfiltered(this, oldStack);
        }
    }

    /** Returns whether tasks are currently filtered */
    public boolean hasFilteredTasks() {
        return mTaskList.hasFilter();
    }

    @Override
    public String toString() {
        String str = "Tasks:\n";
        for (Profile t : mTaskList.getTasks()) {
            str += "  " + t.toString() + "\n";
        }
        return str;
    }
}