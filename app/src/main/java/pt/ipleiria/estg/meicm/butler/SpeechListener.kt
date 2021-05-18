package pt.ipleiria.estg.meicm.butler

import android.speech.SpeechRecognizer
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.MutableLiveData

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