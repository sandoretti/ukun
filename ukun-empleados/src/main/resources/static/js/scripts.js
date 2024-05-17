
function formatearPrecios() {
    var prices = document.querySelectorAll('.precio');

    prices.forEach(function(price) {
        var value = parseFloat(price.textContent).toLocaleString('es-ES', {style: 'currency', currency: 'EUR', minimumFractionDigits: 2});
        price.textContent = value;
    });
}