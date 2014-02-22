# -*- mode: ruby -*-
# vi: set ft=ruby :

# Vagrantfile API/syntax version. Don't touch unless you know what you're doing!
VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  config.vm.box = "precise64"

  config.vm.hostname = "dakait-dev"
  config.vm.box_url = "http://files.vagrantup.com/precise64.box"

  config.vm.network :forwarded_port, guest: 3000, host: 3000

  config.vm.provider :virtualbox do |vb|
	  vb.customize ["modifyvm", :id, "--memory", "2048"]
	  vb.customize ["modifyvm", :id, "--cpus", "2"]   
	  vb.customize ["modifyvm", :id, "--ioapic", "on"]
  end  

  #
  ppaRepos = [
  ]

  # The postgres/gis family of products is not in the list intentionally since they
  # are explicitly installed in one of the scripts
  packageList = [
	  "git",
	  "build-essential",
	  "openjdk-7-jdk"
  ];

  if Dir.glob("#{File.dirname(__FILE__)}/.vagrant/machines/default/*/id").empty?
	  pkg_cmd = ""

	  pkg_cmd << "apt-get update -qq; apt-get install -q -y python-software-properties; "

	  if ppaRepos.length > 0
		  ppaRepos.each { |repo| pkg_cmd << "add-apt-repository -y " << repo << " ; " }
		  pkg_cmd << "apt-get update -qq; "
	  end

	  # install packages we need
	  pkg_cmd << "apt-get install -q -y " + packageList.join(" ") << " ; "

	  # get the latest version of leiningen and setup it up
	  pkg_cmd << "wget -O /usr/bin/lein  https://raw.github.com/technomancy/leiningen/stable/bin/lein ; "
	  pkg_cmd << "chmod +x /usr/bin/lein ; "

	  config.vm.provision :shell, :inline => pkg_cmd
  end
end
