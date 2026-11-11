export const environment = {
  /**
   * Where the backend lives. Empty means the same origin as the app: `ng serve` proxies `/api`
   * to http://localhost:8080 (proxy.conf.json) and the nginx image proxies it to the `app`
   * service. Set a full URL (e.g. https://api.example.com) to call a backend on another origin;
   * that origin's FRONTEND_URL must then be this app's origin for CORS.
   */
  apiUrl: '',
};
