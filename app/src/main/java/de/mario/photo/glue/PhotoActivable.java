package de.mario.photo.glue;

import android.content.Context;
import android.os.Handler;

/**
 * Interface for coupling between the activity and sub classes.
 */
public interface PhotoActivable {

    String PIC_SIZE_KEY = "%sx%s";

    String PICTURES = "pictures";
    String SAVE_FOLDER = "saveFolder";

    String EXPOSURE_MERGE = "exposureMerge";
    String MERGED = "merged";

    String PREPARE_FOR_NEXT = "prepareForNext";

    Handler getMessageHandler();

    Context getContext();
}
