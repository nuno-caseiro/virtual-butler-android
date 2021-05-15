package pt.ipleiria.estg.meicm.butler

import android.content.Intent
import android.media.AudioManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpeechListener(var speech: SpeechRecognizer, var b: MutableLiveData<Boolean>) :
    UtteranceProgressListener() {
    override fun onDone(utteranceId: String?) {

        b.postValue(false)

    }

    override fun onError(utteranceId: String?) {

    }

    override fun onStart(utteranceId: String?) {

    }
}