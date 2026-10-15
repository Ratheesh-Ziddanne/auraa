const db = require('../../database/db');
const SentimentAnalyzer = require('../../ai/sentiment_analysis/sentimentAnalyzer');

const analyzerInstance = new SentimentAnalyzer();

class SentimentController {
    async analyzeSentiment(req, res) {
        try {
            const { text = '', user_id = 1, context = {} } = req.body;
            const result = analyzerInstance.analyze(text, context);

            // Log emotional state to emotion_history table
            await db.run(`
                INSERT INTO emotion_history (user_id, emotion, confidence, input_text, adapted_style)
                VALUES (?, ?, ?, ?, ?)
            `, [user_id, result.emotion, result.confidence, text, result.adaptedStyle]);

            return res.json({
                status: 'SUCCESS',
                ai_mode: req.body.ai_mode || 'ACTIVE',
                model: 'DistilBERT-Emotion / NLP-Speech-Classifier',
                ...result
            });
        } catch (e) {
            console.error('analyzeSentiment error:', e);
            return res.status(500).json({ error: 'Sentiment analysis failed.' });
        }
    }

    getAnalyzer() {
        return analyzerInstance;
    }
}

module.exports = new SentimentController();
