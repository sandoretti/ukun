
function formatearPrecios() {
    var prices = document.querySelectorAll('.precio');

    prices.forEach(function(price) {
        var value = parseFloat(price.textContent).toLocaleString('es-ES', {style: 'currency', currency: 'EUR', minimumFractionDigits: 2});
        price.textContent = value;
    });
}

document.addEventListener('DOMContentLoaded', function() {
    setTimeout(function() {
        const successMessages = document.querySelectorAll('.alert-success');
        const errorMessages = document.querySelectorAll('.alert-danger');
        const infoMessages = document.querySelectorAll('.alert-info');

        successMessages.forEach(function(message) {
            message.style.display = 'none';
        });

        errorMessages.forEach(function(message) {
            message.style.display = 'none';
        });

        infoMessages.forEach(function(message) {
            message.style.display = 'none';
        });

    }, 4000);
});