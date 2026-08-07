import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/products`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/products
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/products/suggest`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/products/suggest
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/products/by-sku/:sku`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/products/by-sku/{sku}
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/products/:id`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/products/{id}
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/product-types`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/product-types
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/availability`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/availability
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/stores`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/stores
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/cart`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/cart
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/cart`, ({ request, params }) => {
    // TODO: Implement mock for DELETE /api/cart
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/cart/items/:productId`, ({ request, params }) => {
    // TODO: Implement mock for PUT /api/cart/items/{productId}
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/cart/items/:productId`, ({ request, params }) => {
    // TODO: Implement mock for DELETE /api/cart/items/{productId}
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/checkout/attempts`, ({ request, params }) => {
    // TODO: Implement mock for POST /api/checkout/attempts
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/orders`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/orders
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/orders`, ({ request, params }) => {
    // TODO: Implement mock for POST /api/orders
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/orders/:id`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/orders/{id}
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/me`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/me
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/me/profile`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/me/profile
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/me/profile`, ({ request, params }) => {
    // TODO: Implement mock for PUT /api/me/profile
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/signup`, ({ request, params }) => {
    // TODO: Implement mock for POST /api/signup
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/stores/:storeId/stock`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/stores/{storeId}/stock
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/stores/:storeId/stock`, ({ request, params }) => {
    // TODO: Implement mock for POST /api/stores/{storeId}/stock
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/stores/:storeId/stock/:lineId`, ({ request, params }) => {
    // TODO: Implement mock for PUT /api/stores/{storeId}/stock/{lineId}
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/stores/:storeId/stock/:lineId`, ({ request, params }) => {
    // TODO: Implement mock for DELETE /api/stores/{storeId}/stock/{lineId}
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/admin/products`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/admin/products
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/admin/products`, ({ request, params }) => {
    // TODO: Implement mock for POST /api/admin/products
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/admin/products/{id}
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id`, ({ request, params }) => {
    // TODO: Implement mock for PUT /api/admin/products/{id}
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id`, ({ request, params }) => {
    // TODO: Implement mock for DELETE /api/admin/products/{id}
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id:discontinue`, ({ request, params }) => {
    // TODO: Implement mock for POST /api/admin/products/{id}:discontinue
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id:reactivate`, ({ request, params }) => {
    // TODO: Implement mock for POST /api/admin/products/{id}:reactivate
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id/image`, ({ request, params }) => {
    // TODO: Implement mock for PUT /api/admin/products/{id}/image
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id/image`, ({ request, params }) => {
    // TODO: Implement mock for DELETE /api/admin/products/{id}/image
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/admin/product-types`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/admin/product-types
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/admin/product-types`, ({ request, params }) => {
    // TODO: Implement mock for POST /api/admin/product-types
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/admin/product-types/:id`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/admin/product-types/{id}
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/admin/product-types/:id`, ({ request, params }) => {
    // TODO: Implement mock for PUT /api/admin/product-types/{id}
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/admin/product-types/:id`, ({ request, params }) => {
    // TODO: Implement mock for DELETE /api/admin/product-types/{id}
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/admin/employees`, ({ request, params }) => {
    // TODO: Implement mock for GET /api/admin/employees
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/admin/employees`, ({ request, params }) => {
    // TODO: Implement mock for POST /api/admin/employees
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/internal/accounts`, ({ request, params }) => {
    // TODO: Implement mock for POST /internal/accounts
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/internal/accounts/:accountId:activate`, ({ request, params }) => {
    // TODO: Implement mock for POST /internal/accounts/{accountId}:activate
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/internal/accounts/:accountId:release`, ({ request, params }) => {
    // TODO: Implement mock for POST /internal/accounts/{accountId}:release
    return HttpResponse.json({});
  }),
];
