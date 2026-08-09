import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/products`, () => {
    // TODO: Implement mock for GET /api/products
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/products/suggest`, () => {
    // TODO: Implement mock for GET /api/products/suggest
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/products/by-sku/:sku`, () => {
    // TODO: Implement mock for GET /api/products/by-sku/{sku}
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/products/:id`, () => {
    // TODO: Implement mock for GET /api/products/{id}
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/product-types`, () => {
    // TODO: Implement mock for GET /api/product-types
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/availability`, () => {
    // TODO: Implement mock for GET /api/availability
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/stores`, () => {
    // TODO: Implement mock for GET /api/stores
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/cart`, () => {
    // TODO: Implement mock for GET /api/cart
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/cart`, () => {
    // TODO: Implement mock for DELETE /api/cart
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/cart/items/:productId`, () => {
    // TODO: Implement mock for PUT /api/cart/items/{productId}
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/cart/items/:productId`, () => {
    // TODO: Implement mock for DELETE /api/cart/items/{productId}
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/checkout/attempts`, () => {
    // TODO: Implement mock for POST /api/checkout/attempts
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/orders`, () => {
    // TODO: Implement mock for GET /api/orders
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/orders`, () => {
    // TODO: Implement mock for POST /api/orders
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/orders/:id`, () => {
    // TODO: Implement mock for GET /api/orders/{id}
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/me`, () => {
    // TODO: Implement mock for GET /api/me
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/me/profile`, () => {
    // TODO: Implement mock for GET /api/me/profile
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/me/profile`, () => {
    // TODO: Implement mock for PUT /api/me/profile
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/signup`, () => {
    // TODO: Implement mock for POST /api/signup
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/stores/:storeId/stock`, () => {
    // TODO: Implement mock for GET /api/stores/{storeId}/stock
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/stores/:storeId/stock`, () => {
    // TODO: Implement mock for POST /api/stores/{storeId}/stock
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/stores/:storeId/stock/:lineId`, () => {
    // TODO: Implement mock for PUT /api/stores/{storeId}/stock/{lineId}
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/stores/:storeId/stock/:lineId`, () => {
    // TODO: Implement mock for DELETE /api/stores/{storeId}/stock/{lineId}
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/admin/products`, () => {
    // TODO: Implement mock for GET /api/admin/products
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/admin/products`, () => {
    // TODO: Implement mock for POST /api/admin/products
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id`, () => {
    // TODO: Implement mock for GET /api/admin/products/{id}
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id`, () => {
    // TODO: Implement mock for PUT /api/admin/products/{id}
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id`, () => {
    // TODO: Implement mock for DELETE /api/admin/products/{id}
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id:discontinue`, () => {
    // TODO: Implement mock for POST /api/admin/products/{id}:discontinue
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id:reactivate`, () => {
    // TODO: Implement mock for POST /api/admin/products/{id}:reactivate
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id/image`, () => {
    // TODO: Implement mock for PUT /api/admin/products/{id}/image
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/admin/products/:id/image`, () => {
    // TODO: Implement mock for DELETE /api/admin/products/{id}/image
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/admin/product-types`, () => {
    // TODO: Implement mock for GET /api/admin/product-types
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/admin/product-types`, () => {
    // TODO: Implement mock for POST /api/admin/product-types
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/admin/product-types/:id`, () => {
    // TODO: Implement mock for GET /api/admin/product-types/{id}
    return HttpResponse.json({});
  }),
  http.put(`${import.meta.env.VITE_API_URL || ''}/api/admin/product-types/:id`, () => {
    // TODO: Implement mock for PUT /api/admin/product-types/{id}
    return HttpResponse.json({});
  }),
  http.delete(`${import.meta.env.VITE_API_URL || ''}/api/admin/product-types/:id`, () => {
    // TODO: Implement mock for DELETE /api/admin/product-types/{id}
    return HttpResponse.json({});
  }),
  http.get(`${import.meta.env.VITE_API_URL || ''}/api/admin/employees`, () => {
    // TODO: Implement mock for GET /api/admin/employees
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/api/admin/employees`, () => {
    // TODO: Implement mock for POST /api/admin/employees
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/internal/accounts`, () => {
    // TODO: Implement mock for POST /internal/accounts
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/internal/accounts/:accountId:activate`, () => {
    // TODO: Implement mock for POST /internal/accounts/{accountId}:activate
    return HttpResponse.json({});
  }),
  http.post(`${import.meta.env.VITE_API_URL || ''}/internal/accounts/:accountId:release`, () => {
    // TODO: Implement mock for POST /internal/accounts/{accountId}:release
    return HttpResponse.json({});
  }),
];
