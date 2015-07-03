
$(document).ready(function () {
    console.log('XXX calling sidebar resizable');
    $("#sidebar").resizable({
        handles: 'e',
        minWidth: 150,
        maxWidth: 1200,
        resize: function (event, ui) {
            console.log('XXX sidebar resize');
            var x = ui.element.outerWidth();
            //var y=ui.element.outerHeight();
            var par = $(this).parent().width();
            var ele = ui.element;
            var factor = par - x;

            if (x == par) {
//            jsEditor.resize();
//            cssEditor.resize();
//            htmEditor.resize();
                return;
            }

            $.each(ele.siblings(), function (idx, item) {

                //ele.siblings().eq(idx).css('height',y+'px');
                ele.siblings().eq(idx).css('width', (factor) + 'px');

            });

            if (x >= (par - 100)) {
                $("#sidebar").resizable("option", "maxWidth", ui.size.width);
                return;
            }

//        jsEditor.resize();
//        cssEditor.resize();
//        htmEditor.resize();
        }
    });
});







