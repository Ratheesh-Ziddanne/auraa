/**
 * Voice Assistant Component
 * Web Speech Recognition (STT), Speech Synthesis (TTS) with emotion-adapted prosody,
 * haptic vibration feedback, and voice command parser.
 */

class VoiceAssistant {
    constructor(options = {}) {
        this.onCommand = options.onCommand || null;
        this.onTranscriptUpdate = options.onTranscriptUpdate || null;
        this.onListeningChange = options.onListeningChange || null;

        this.recognition = null;
        this.isListening = false;
        this.lastSpokenText = '';
        this.currentEmotion = 'CALM';
        this.currentProsody = { rate: 1.0, pitch: 1.0 };

        this.initSpeechRecognition();
    }

    initSpeechRecognition() {
        const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRec) {
            console.warn('SpeechRecognition API not available in this browser. Fallback to text command input.');
            return;
        }

        this.recognition = new SpeechRec();
        this.recognition.continuous = true;
        this.recognition.interimResults = true;
        this.recognition.lang = 'en-US';

        this.recognition.onstart = () => {
            this.isListening = true;
            if (this.onListeningChange) this.onListeningChange(true);
        };

        this.recognition.onresult = (event) => {
            let interimTranscript = '';
            let finalTranscript = '';

            for (let i = event.resultIndex; i < event.results.length; ++i) {
                if (event.results[i].isFinal) {
                    finalTranscript += event.results[i][0].transcript;
                } else {
                    interimTranscript += event.results[i][0].transcript;
                }
            }

            const current = finalTranscript || interimTranscript;
            if (this.onTranscriptUpdate && current) {
                this.onTranscriptUpdate(current);
            }

            if (finalTranscript && this.onCommand) {
                this.onCommand(finalTranscript.trim());
            }
        };

        this.recognition.onerror = (event) => {
            console.warn('Speech recognition event:', event.error);
        };

        this.recognition.onend = () => {
            // Restart if continuous listening requested
            if (this.isListening) {
                try { this.recognition.start(); } catch (e) {}
            } else {
                if (this.onListeningChange) this.onListeningChange(false);
            }
        };
    }

    toggleListening() {
        if (!this.recognition) {
            const manual = prompt('Voice input not supported directly in this browser. Type your voice command:');
            if (manual && this.onCommand) this.onCommand(manual);
            return;
        }

        if (this.isListening) {
            this.isListening = false;
            try { this.recognition.stop(); } catch (e) {}
            if (this.onListeningChange) this.onListeningChange(false);
        } else {
            try {
                this.recognition.start();
                this.isListening = true;
                if (this.onListeningChange) this.onListeningChange(true);
            } catch (e) {
                console.warn('Failed to start speech recognition:', e.message);
            }
        }
    }

    setEmotionProsody(emotion, prosody) {
        this.currentEmotion = emotion;
        if (prosody) {
            this.currentProsody = prosody;
        }
    }

    /**
     * Speaks aloud using Web SpeechSynthesis, adapting speech rate and pitch.
     */
    speak(text, onDone = null) {
        if (!text || !window.speechSynthesis) return;
        this.lastSpokenText = text;

        window.speechSynthesis.cancel(); // Flush previous utterances immediately

        const utterance = new SpeechSynthesisUtterance(text);
        utterance.rate = this.currentProsody.rate || 1.0;
        utterance.pitch = this.currentProsody.pitch || 1.0;
        utterance.volume = 1.0;

        utterance.onend = () => {
            if (onDone) onDone();
        };

        window.speechSynthesis.speak(utterance);
    }

    repeatLast() {
        if (this.lastSpokenText) {
            this.speak(this.lastSpokenText);
        } else {
            this.speak("There is nothing to repeat. You are at Block A Entrance.");
        }
    }

    /**
     * Triggers mobile haptic vibration feedback patterns
     */
    triggerHaptic(pattern = 'TAP') {
        if (!navigator.vibrate) return;
        switch (pattern) {
            case 'RAPID_CONTINUOUS':
                navigator.vibrate([150, 50, 150, 50, 200]);
                break;
            case 'DOUBLE_PULSE':
                navigator.vibrate([100, 80, 100]);
                break;
            case 'GENTLE_PULSE':
                navigator.vibrate([70]);
                break;
            case 'TAP':
            default:
                navigator.vibrate([40]);
                break;
        }
    }
}

window.VoiceAssistant = VoiceAssistant;
