/**
 * NLP & Speech Sentiment / Emotion Analysis Module
 * Detects user emotional state (CALM, HAPPY, CONFUSED, ANXIOUS, FRUSTRATED, STRESSED, URGENT)
 * and determines dynamic conversational guidance adaptations.
 */

class SentimentAnalyzer {
    constructor() {
        // Keyword and pattern lexicons
        this.EMOTION_PATTERNS = {
            URGENT: [
                'emergency', 'sos', 'danger', 'i fell', 'fallen', 
                'trapped', 'fire', 'ambulance', 'call 911', 'call 112', 'medical emergency'
            ],
            ANXIOUS: [
                'scared', 'afraid', 'nervous', 'worried', 'panic', 'what if', 
                'am i safe', 'please tell me', 'is someone there', 'where am i going',
                'i don\'t know what to do', 'freaking out', 'terrified', 'dark'
            ],
            CONFUSED: [
                'where am i', 'lost', 'i don\'t know where i am', 'i am lost', 'which way', 
                'is this right', 'i don\'t understand', 'what did you say', 'repeat', 
                'pardon', 'huh', 'wrong turn', 'where is', 'i thought', 'am i in',
                'please help me', 'help me find', 'help me navigate', 'can you help me'
            ],
            FRUSTRATED: [
                'stop', 'shut up', 'stupid', 'annoying', 'wrong', 'you are useless', 
                'ugh', 'again', 'not working', 'hate this', 'too slow', 'hurry up',
                'be quiet', 'listen to me'
            ],
            STRESSED: [
                'running late', 'late', 'hurry', 'so much noise', 'too loud', 
                'headache', 'tired', 'exhausted', 'can\'t find', 'hard to walk', 'heavy'
            ],
            HAPPY: [
                'thank you', 'thanks', 'great', 'awesome', 'good job', 'perfect', 
                'wonderful', 'love this', 'yay', 'feeling good', 'excellent', 'nice'
            ],
            CALM: [
                'take me to', 'navigate to', 'take me', 'guide me', 'what is', 
                'how far', 'distance', 'time', 'status', 'okay', 'yes', 'no', 'continue'
            ]
        };
    }

    /**
     * Analyzes input text / voice transcript and returns emotion classification with confidence.
     * @param {string} text 
     * @param {object} context Optional conversational context (e.g. repeated emergency requests)
     * @returns {object} { emotion, confidence, adaptedStyle, adaptationPrompt }
     */
    analyze(text, context = {}) {
        if (!text || typeof text !== 'string' || text.trim() === '') {
            return {
                emotion: 'CALM',
                confidence: 0.90,
                adaptedStyle: 'STANDARD_GUIDANCE',
                adaptationPrompt: 'Provide standard steady guidance with distance and direction.'
            };
        }

        const lower = text.toLowerCase().trim();
        const scores = {
            URGENT: 0,
            ANXIOUS: 0,
            CONFUSED: 0,
            FRUSTRATED: 0,
            STRESSED: 0,
            HAPPY: 0,
            CALM: 0.1 // Baseline prior
        };

        // Repeated emergency or distress multiplier
        if (context.repeatedDistressCount && context.repeatedDistressCount > 1) {
            scores.URGENT += 2.0;
            scores.ANXIOUS += 1.5;
        }

        // Punctuation and exclamation indicators
        if (lower.includes('!')) scores.URGENT += 0.3;
        if (lower.includes('??') || lower.includes('?!')) scores.CONFUSED += 0.4;

        // Pattern matching
        for (const [emotion, keywords] of Object.entries(this.EMOTION_PATTERNS)) {
            for (const kw of keywords) {
                if (lower === kw) {
                    scores[emotion] += 1.8;
                } else if (lower.includes(kw)) {
                    scores[emotion] += 1.0;
                }
            }
        }

        // Determine highest scoring emotion
        let topEmotion = 'CALM';
        let maxScore = -1;
        let totalScore = 0;

        for (const [emotion, score] of Object.entries(scores)) {
            totalScore += score;
            if (score > maxScore) {
                maxScore = score;
                topEmotion = emotion;
            }
        }

        // Calculate confidence
        let confidence = 0.85;
        if (totalScore > 0) {
            const rawRatio = maxScore / totalScore;
            confidence = Math.min(0.98, Math.max(0.72, parseFloat((rawRatio * 0.4 + 0.6).toFixed(2))));
        }

        const adaptation = this.getAdaptationStrategy(topEmotion);

        return {
            emotion: topEmotion,
            confidence,
            input_text: text,
            adaptedStyle: adaptation.style,
            adaptationPrompt: adaptation.prompt,
            speechProsody: adaptation.prosody
        };
    }

    /**
     * Connects sentiment with navigation guidance style (Section 4).
     */
    getAdaptationStrategy(emotion) {
        switch (emotion) {
            case 'CONFUSED':
                return {
                    style: 'SUPPORTIVE_DETAILED',
                    prompt: 'Reassure the user immediately. Give concrete visual/tactile landmarks (doors, floor texture, walls).',
                    prosody: { rate: 0.9, pitch: 1.05 }
                };
            case 'ANXIOUS':
                return {
                    style: 'REASSURING_STEP_BY_STEP',
                    prompt: 'Use short, calming instructions. Tell user they are safe and to pause before moving.',
                    prosody: { rate: 0.85, pitch: 1.0 }
                };
            case 'FRUSTRATED':
                return {
                    style: 'CONCISE_DIRECT',
                    prompt: 'Skip extraneous details. Give direct, unambiguous turns and distances only.',
                    prosody: { rate: 1.05, pitch: 0.95 }
                };
            case 'URGENT':
                return {
                    style: 'SAFETY_PRIORITY',
                    prompt: 'Immediately halt user if movement is dangerous. Guide directly to closest exit or safe zone.',
                    prosody: { rate: 1.1, pitch: 1.15 }
                };
            case 'STRESSED':
                return {
                    style: 'CLEAR_PACED',
                    prompt: 'Provide calm pacing cues. Warn well in advance of turns.',
                    prosody: { rate: 0.95, pitch: 1.0 }
                };
            case 'HAPPY':
                return {
                    style: 'WARM_INTERACTIVE',
                    prompt: 'Keep voice pleasant and affirmative as milestones are reached.',
                    prosody: { rate: 1.0, pitch: 1.05 }
                };
            case 'CALM':
            default:
                return {
                    style: 'STANDARD_GUIDANCE',
                    prompt: 'Provide smooth, natural turn-by-turn guidance with distances in metres.',
                    prosody: { rate: 1.0, pitch: 1.0 }
                };
        }
    }
}

module.exports = SentimentAnalyzer;
