// Externs for the qTip2 library used in this project

jQuery.prototype.qtip = function(o) {};
$.prototype.qtip = function(o) {};

// a hack to trick ClojureScript to let us use qTip2 API destroy
$.prototype.destroy = function() {};
