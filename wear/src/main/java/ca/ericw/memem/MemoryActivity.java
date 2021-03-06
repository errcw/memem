package ca.ericw.memem;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Activity implementing the game. */
public class MemoryActivity extends Activity {

  public MemoryActivity() {
    _expectedEntries = new ArrayList<>();
    _random = new Random();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.memory);

    _buttons = new Button[] {
        (Button) findViewById(R.id.top_left_button),
        (Button) findViewById(R.id.top_right_button),
        (Button) findViewById(R.id.bottom_left_button),
        (Button) findViewById(R.id.bottom_right_button),
    };
    for (final Entry entry : ENTRIES) {
      _buttons[entry.ordinal()].setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          onEntryClicked(entry);
        }
      });
    }

    _buttonColors = new int[][] {
        new int[] {
            getResources().getColor(R.color.top_left_color),
            getResources().getColor(R.color.top_left_highlight_color) },
        new int[] {
            getResources().getColor(R.color.top_right_color),
            getResources().getColor(R.color.top_right_highlight_color) },
        new int[] {
            getResources().getColor(R.color.bottom_left_color),
            getResources().getColor(R.color.bottom_left_highlight_color) },
        new int[] {
            getResources().getColor(R.color.bottom_right_color),
            getResources().getColor(R.color.bottom_right_highlight_color) },
    };

    _startView = (TextView) findViewById(R.id.start_view);
    _startView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onStartClicked();
      }
    });

    _scoreView = (TextView) findViewById(R.id.score_view);

    _vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    _startView.setVisibility(View.VISIBLE);
    // Buttons will be enabled once the first sequence has been shown.
    setButtonsEnabled(false);
  }

  /** Indicates the start banner was clicked. */
  private void onStartClicked() {
    int cx = (_startView.getLeft() + _startView.getRight()) / 2;
    int cy = (_startView.getTop() + _startView.getBottom()) / 2;
    int radius = Math.max(_startView.getWidth(), _startView.getHeight());
    Animator hideStartAnim = ViewAnimationUtils.createCircularReveal(_startView, cx, cy, radius, 0);
    hideStartAnim.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(Animator anim) {
        _startView.setVisibility(View.INVISIBLE);
        startNewGame();
      }
    });
    hideStartAnim.start();
  }

  /** Indicates the given entry was clicked. */
  private void onEntryClicked(Entry entry) {
    if (_currentExpectedEntry >= _expectedEntries.size()) {
      // Could happen if we receive an event before the game is initialized.
      Log.d(TAG, "Received unexpected click for " + entry);
      return;
    }

    Entry expected = _expectedEntries.get(_currentExpectedEntry);
    if (entry == expected) {
      _currentExpectedEntry += 1;
      if (_currentExpectedEntry >= _expectedEntries.size()) {
        _vibrator.vibrate(GAME_EXTENDED_VIBRATION, -1, VIBRATION_ATTRIBUTES);
        extendGame();
      }
    } else {
      showEndGame();
    }
  }

  /** Starts a new game. */
  private void startNewGame() {
    _expectedEntries.clear();
    extendGame();
  }

  /** Extends the game by adding one entry to the sequence. */
  private void extendGame() {
    _expectedEntries.add(ENTRIES[(_random.nextInt(ENTRIES.length))]);
    _currentExpectedEntry = 0;
    showSequence();
  }

  /** Shows the color sequence to the player. */
  private void showSequence() {
    Log.d(TAG, _expectedEntries.toString());

    List<Animator> tintAnimations = new ArrayList<>(_expectedEntries.size());
    for (Entry entry : _expectedEntries) {
      ObjectAnimator tintAnimation = ObjectAnimator.ofArgb(
          _buttons[entry.ordinal()].getBackground(), "tint",
          _buttonColors[entry.ordinal()][0],
          _buttonColors[entry.ordinal()][1],
          _buttonColors[entry.ordinal()][0]);
      tintAnimation.setDuration(TINT_FLASH_MS);
      tintAnimations.add(tintAnimation);
    }

    AnimatorSet sequenceAnimation = new AnimatorSet();
    sequenceAnimation.setStartDelay(TINT_DELAY_MS);
    sequenceAnimation.playSequentially(tintAnimations);
    sequenceAnimation.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationStart(Animator animation) {
        setButtonsEnabled(false);
      }
      @Override public void onAnimationEnd(Animator animation) {
        setButtonsEnabled(true);
      }
    });
    sequenceAnimation.start();
  }

  /** Shows a reset animation to introduce a break between games. */
  private void showEndGame() {
    AnimatorSet endAnimation = new AnimatorSet();

    int cx = (_scoreView.getLeft() + _scoreView.getRight()) / 2;
    int cy = (_scoreView.getTop() + _scoreView.getBottom()) / 2;
    int radius = Math.max(_scoreView.getWidth(), _scoreView.getHeight());

    // Show the score.
    Animator showScoreAnim = ViewAnimationUtils.createCircularReveal(_scoreView, cx, cy, 0, radius);
    _scoreView.setText(String.valueOf(_expectedEntries.size() - 1));
    _scoreView.setVisibility(View.VISIBLE);

    // At the same time fade out the squares.
    for (Entry entry : ENTRIES) {
      ObjectAnimator tintAnimation = ObjectAnimator.ofArgb(
          _buttons[entry.ordinal()].getBackground(), "tint",
          _buttonColors[entry.ordinal()][0], _buttonColors[entry.ordinal()][1]);
      tintAnimation.setDuration(RESET_TRANSITION_MS);
      endAnimation.play(showScoreAnim).with(tintAnimation);
    }

    // After a delay hide the score.
    Animator hideScoreAnim = ViewAnimationUtils.createCircularReveal(_scoreView, cx, cy, radius, 0);
    endAnimation.play(hideScoreAnim).after(SHOW_SCORE_MS).after(showScoreAnim);

    // And at the same time fade the squares back in.
    for (Entry entry : ENTRIES) {
      ObjectAnimator tintAnimation = ObjectAnimator.ofArgb(
          _buttons[entry.ordinal()].getBackground(), "tint",
          _buttonColors[entry.ordinal()][1], _buttonColors[entry.ordinal()][0]);
      tintAnimation.setDuration(RESET_TRANSITION_MS);
      endAnimation.play(hideScoreAnim).with(tintAnimation);
    }

    endAnimation.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(Animator animation) {
        _scoreView.setVisibility(View.INVISIBLE);
        startNewGame();
      }
    });
    endAnimation.start();

    _vibrator.vibrate(GAME_ENDED_VIBRATION, VIBRATION_ATTRIBUTES);

    // Buttons will be enabled once the new sequence is shown.
    setButtonsEnabled(false);
  }

  /** Configures whether the player input buttons are enabled. */
  private void setButtonsEnabled(boolean enabled) {
    for (Button button : _buttons) {
      button.setEnabled(enabled);
    }
  }

  /** Describes an entry in the sequence. */
  private static enum Entry {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
  }
  private static final Entry[] ENTRIES = Entry.values();

  private final List<Entry> _expectedEntries;
  private int _currentExpectedEntry;

  private Button[] _buttons;
  private int[][] _buttonColors;
  private TextView _scoreView;
  private TextView _startView;

  private Vibrator _vibrator;

  private final Random _random;

  private static final int TINT_DELAY_MS = 500;
  private static final int TINT_FLASH_MS = 300;
  private static final int RESET_TRANSITION_MS = 250;
  private static final int SHOW_SCORE_MS = 1000;

  private static final long[] GAME_EXTENDED_VIBRATION = new long[] {200, 100};
  private static final long GAME_ENDED_VIBRATION = 500;

  private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .setUsage(AudioAttributes.USAGE_GAME)
      .build();

  private static final String TAG = "Memem";
}
