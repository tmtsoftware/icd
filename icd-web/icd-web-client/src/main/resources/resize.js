// Make the sidebar resizable using jquery-ui resizable
$(document).ready(function () {
    $("#sidebar").resizable({
        handles: 'e',
        minWidth: 150,
        maxWidth: 1200,
        resize: function (event, ui) {
            var x = ui.element.outerWidth();
            //var y=ui.element.outerHeight();
            var par = $(this).parent().width();
            var ele = ui.element;
            var factor = par - x;

            if (x == par) {
                return;
            }

            $.each(ele.siblings(), function (idx, item) {
                //ele.siblings().eq(idx).css('height',y+'px');
                ele.siblings().eq(idx).css('width', (factor) + 'px');
            });

            if (x >= (par - 100)) {
                $("#sidebar").resizable("option", "maxWidth", ui.size.width);
            }
        }
    });
});







