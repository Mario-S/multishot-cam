package de.mario.photo.controller.media;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import de.mario.photo.R;

/**
 *
 */
abstract class AbstractOpener {
    private final Context context;

    AbstractOpener(Context context) {
        this.context = context;
    }

    protected Context getContext() {
        return context;
    }

    protected Intent newIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    protected String getText(int id) {
        return context.getString(id);
    }

    protected void showText(String txt) {
        Toast.makeText(context, txt, Toast.LENGTH_LONG).show();
    }

    protected void startActivity(Intent intent) {
        context.startActivity(intent);
    }

    protected boolean isSupported(Intent intent) {
        return intent.resolveActivity(context.getPackageManager()) != null;
    }

    protected void tryOpen(Intent intent) {
        if (isSupported(intent)) {
            startActivity(intent);
        } else {
            showText(getText(R.string.no_gallery_app));
        }
    }
}
