define([
   'backbone',
   'rivets',
], function(Backbone, rivets) {
    return Backbone.View.extend({
        _do_init : function() {
            $tmpl = $(this.template);
            iamBusy();

            this.scope.$el = this.$el;
            this.scope.close = this.close;
            this.scope.help_flipped = false,
            this.scope.help_icon_class = 'help-link',

            this.$el.append($('.body', $tmpl));
            this.$el.appendTo('body');

            var title = $.i18n.prop($('.title', $tmpl).attr('data-msg'));
            this.$el.dialog({
                title: title,
                autoOpen: false,  // assume the three params are fixed for all dialogs
                modal: true,
                width: this.scope.width ? this.scope.width : 600,
                maxHeight: this.scope.maxHeight ? this.scope.maxHeight : false,
                height: this.scope.height ? this.scope.height : 'auto',
                dialogClass: 'euca-dialog-container',
                show: 'fade',
                // don't add hide: 'fade' here b/c it causes an issue with positioning the dialog next to another dialog
                resizable: false,
                closeOnEscape : true,
                position: { my: 'center', at: 'center', of: window, collision: 'none'},
                open: function(event, ui) {
                },
                close: function(event, ui) {
                }
              });

            this.rivetsView = rivets.bind(this.$el, this.scope);
            this.render();

            $titleBar = this.scope.$el.parent().find('.ui-dialog-titlebar');
            if($titleBar.find('.' + this.scope.help_icon_class).length <= 0)
              $titleBar.append($('<div>')
                .addClass(this.scope.help_icon_class)
                .append($('<a>').attr('href','#').text('?')));
            this.setHelp(this.$el.parent(), title);

            this.$el.dialog('open');
        },
        close : function() {
            this.$el.dialog('close');
        },
        render : function() {
            this.rivetsView.sync();
            return this;
        },
        setHelp : function($dialog, title) {
          var self = this;
          var $help = this.scope.help;
          var $titleBar = $dialog.find('.ui-dialog-titlebar');
          var $helpLink = $titleBar.find('.'+this.scope.help_icon_class+' a');
          if(!$help || !$help.content || $help.content.length <= 0){
            $helpLink.remove();
            console.log("removed help link");
            return;
          }
          var $buttonPane = $dialog.find('.ui-dialog-buttonpane');
          var $thedialog = $dialog.find('.euca-dialog');
          var helpContent = this.scope.help ? this.scope.help.content : '';
          this.$el.find('.dialog-help-content').html(helpContent);
          var $helpPane = this.$el.find('dialog-help');
          $helpLink.click(function(evt) {
            if(!self.scope.help_flipped){ 
              self.$el.data('dialog').option('closeOnEscape', false);
              $buttonPane.hide();
              $thedialog.flippy({
                verso:$helpPane,
                direction:"LEFT",
                duration:"300",
                depth:"1.0",
                onFinish : function() {
                  self.scope.$el.find('.help-revert-button a').click( function(evt) {
                    $thedialog.flippyReverse();
                  });
                  self.scope.$el.find('.help-link a').click( function(evt) {
                    $thedialog.flippyReverse();
                  });       
                  if(!self.scope.help_flipped){
                    self.scope.help_flipped = true;
                    self.scope.$el.find('.help-link').removeClass().addClass('help-return').before(
                      $('<div>').addClass('help-popout').append(
                        $('<a>').attr('href','#').text('popout').click(function(e){
                          if(help.url){
                            if(help.pop_height)
                              popOutPageHelp(help.url, help.pop_height);
                            else
                              popOutPageHelp(help.url);
                          }
                          self.scope.$el.find('.help-revert-button a').trigger('click');
                        })
                      )
                    );
                  }else{
                    self.scope.help_flipped = false;
                    self.scope.element.find('.help-return').removeClass().addClass('help-link');
                    self.scope.element.find('.help-popout').detach();
                    $buttonPane.show();
                  }
                  
                }
              });
            }
          });
          self.$el.find('.help-revert-button a').click( function(evt) {
            $helpLink.trigger('click');
          });
        },
	});
});