/*global Java, exports, org, __plugin */
var bkEventPriority = org.bukkit.event.EventPriority,
  bkHandlerList = org.bukkit.event.HandlerList,
  bkPluginManager = org.bukkit.Bukkit.pluginManager;

// Generate a marker Listener on either supported JavaScript engine.
var ScriptCraftListener = typeof Java !== 'undefined'
  ? Java.extend(org.bukkit.event.Listener, {})
  : null;

exports.on = function(
  /* Java Class */
  eventType,
  /* function( registeredListener, event) */

  handler,
  /* (optional) String (HIGH, HIGHEST, LOW, LOWEST, NORMAL, MONITOR), */
  priority
) {
  if (typeof priority == 'undefined') {
    priority = bkEventPriority.HIGHEST;
  } else {
    priority = bkEventPriority[priority.toUpperCase().trim()];
  }

  var result = {};
  var eventExecutor = function(l, evt) {
    function cancel() {
      if (evt instanceof org.bukkit.event.Cancellable) {
        evt.setCancelled(true);
      }
    }
    /*
     let handlers use this.cancel() to cancel the current event
     or this.unregister() to unregister from future events.
     */
    var bound = {};
    for (var i in result) {
      bound[i] = result[i];
    }
    bound.cancel = cancel;
    handler.call(bound, evt, cancel);
  };

  // Create an instance of our empty Listener implementation to track the handler
  var listener = typeof Java !== 'undefined'
    ? new ScriptCraftListener()
    : new JavaAdapter(org.bukkit.event.Listener, {});

  bkPluginManager.registerEvent(
    eventType.class,
    listener,
    priority,
    eventExecutor,
    __plugin
  );

  result.unregister = function() {
    bkHandlerList.unregisterAll(listener);
  };

  return result;
};
