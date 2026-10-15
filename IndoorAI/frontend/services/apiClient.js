/**
 * IndoorAI REST API Client
 * Wraps all backend endpoints with robust error handling and fallbacks.
 */

class ApiClient {
    constructor(baseUrl = '') {
        this.baseUrl = baseUrl || window.location.origin;
    }

    async request(endpoint, options = {}) {
        const url = `${this.baseUrl}/api${endpoint}`;
        const headers = {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        };

        try {
            const res = await fetch(url, { ...options, headers });
            const data = await res.json();
            if (!res.ok) {
                throw new Error(data.error || `HTTP ${res.status}: ${res.statusText}`);
            }
            return data;
        } catch (err) {
            console.warn(`API call failed for ${endpoint}:`, err.message);
            throw err;
        }
    }

    async getBuildings() {
        return this.request('/buildings');
    }

    async getLocations(buildingId, floor) {
        let q = '';
        if (buildingId) q += `building_id=${buildingId}&`;
        if (floor !== undefined) q += `floor=${floor}&`;
        return this.request(`/locations?${q}`);
    }

    async getRoute(source, destination, blockedNear = null, recalculate = false) {
        let q = `source=${encodeURIComponent(source)}&destination=${encodeURIComponent(destination)}`;
        if (recalculate && blockedNear) {
            q += `&recalculate=true&blockedNear=${encodeURIComponent(blockedNear)}`;
        }
        return this.request(`/route?${q}`);
    }

    async startNavigation(source, destination, userId = 1) {
        return this.request('/navigation/start', {
            method: 'POST',
            body: JSON.stringify({ source, destination, user_id: userId })
        });
    }

    async stopNavigation(navigationId, duration = 60, status = 'COMPLETED') {
        return this.request('/navigation/stop', {
            method: 'POST',
            body: JSON.stringify({ navigation_id: navigationId, duration, status })
        });
    }

    async detectObstacle(obstaclePayload) {
        return this.request('/obstacle/detect', {
            method: 'POST',
            body: JSON.stringify(obstaclePayload)
        });
    }

    async analyzeSentiment(text, context = {}) {
        return this.request('/sentiment/analyze', {
            method: 'POST',
            body: JSON.stringify({ text, context })
        });
    }

    async decideAction(decisionState) {
        return this.request('/decision/decide', {
            method: 'POST',
            body: JSON.stringify(decisionState)
        });
    }

    async triggerEmergency(currentLocation = 'Block A Entrance', reason = 'Manual SOS') {
        return this.request('/emergency', {
            method: 'POST',
            body: JSON.stringify({ currentLocation, reason })
        });
    }

    async getNavigationHistory(userId = null) {
        const q = userId ? `?user_id=${userId}` : '';
        return this.request(`/navigation/history${q}`);
    }

    async getEmotionHistory(userId = null) {
        const q = userId ? `?user_id=${userId}` : '';
        return this.request(`/emotion/history${q}`);
    }
}

window.apiClient = new ApiClient();
