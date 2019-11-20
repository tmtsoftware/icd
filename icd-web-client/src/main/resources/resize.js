$(document).ready(function () {

    // Make the sidebar resizable using jquery-ui resizable
    $("#sidebar").resizable({
        handles: 'e',
        minWidth: 150,
        maxWidth: 1200,
        resize: function (event, ui) {
            var x = ui.element.outerWidth();
            var par = $(this).parent().width();
            var ele = ui.element;
            var factor = par - x;

            if (x === par) {
                return;
            }

            $.each(ele.siblings(), function (idx, item) {
                ele.siblings().eq(idx).css('width', (factor) + 'px');
            });

            if (x >= (par - 100)) {
                $("#sidebar").resizable("option", "maxWidth", ui.size.width);
            }
        }
    });

    // Implement Expand/Collapse toolbar button
    // (based on http://jsfiddle.net/KyleMit/f8ypa/)
    var navbarExpandAll = false;
    $('#expand-init').click(function () {
        if (navbarExpandAll) {
            navbarExpandAll = false;
            $('.panel-collapse').collapse('hide');
        } else {
            navbarExpandAll = true;
            $('.panel-collapse').collapse('show');
        }
    });

});
